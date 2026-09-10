package sql_phase2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.Scanner;

public class DbApp {

	Connection conn;

	public DbApp() {
		try {
			Class.forName("org.postgresql.Driver");
			System.out.println("Driver found!\n");
		} catch (ClassNotFoundException e) {
			System.out.println("Driver not found. Please check the build path!");
		}
	}

	public void dbConnect(String ip, String dbname, String username, String password) {
		try {
			conn = DriverManager.getConnection("jdbc:postgresql://" + ip + ":5432/" + dbname, username, password);
			System.out.println("\nConnection is successfull!\nConn:" + conn + "\n");
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	public void dbClose() {
		try {
			conn.close();
			System.out.println("Disconnect from database\n");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static String waitForInput(String prompt) {
		Scanner scn = new Scanner(System.in);
		System.out.println(prompt);
		return scn.nextLine();
	}

	public void showGrades() {
		String amka = waitForInput("Enter student's amka: ");
		int acYear = Integer.parseInt(waitForInput("Enter academic year: "));
		String acSeason = waitForInput("Enter academic season (winter or spring): ");
		
		try {
			PreparedStatement pst = conn
					.prepareStatement("SELECT p.amka, p.name, p.surname FROM \"Person\" p, \"Student\" s \r\n"
							+ "WHERE p.amka = s.amka AND p.amka = ?");
			pst.setString(1, amka);
			ResultSet res1 = pst.executeQuery();

			pst = conn.prepareStatement("SELECT lm.course_code, lm.title, wg.grade\r\n"
					+ "FROM \"LabModule\" lm, \"Workgroup\" wg, \"Joins\" j, \"CourseRun\" cr, \"Semester\" s \r\n"
					+ "WHERE j.amka = ?\r\n" + "AND j.\"wgID\" = wg.\"wgID\"\r\n" + "AND j.module_no = wg.module_no\r\n"
					+ "AND wg.module_no = lm.module_no\r\n" + "AND j.serial_number = wg.serial_number\r\n"
					+ "AND wg.serial_number = lm.serial_number\r\n" + "AND j.course_code = wg.course_code\r\n"
					+ "AND wg.course_code = lm.course_code \r\n" + "AND lm.course_code = cr.course_code\r\n"
					+ "AND cr.semesterrunsin = s.semester_id\r\n" + "AND s.academic_year = ?\r\n"
					+ "AND s.academic_season = ?::semester_season_type \r\n"
					+ "GROUP BY lm.course_code, lm.title, wg.grade");
			pst.setString(1, amka);
			pst.setInt(2, acYear);
			pst.setString(3, acSeason);
			ResultSet res2 = pst.executeQuery();

			if (res1.next()) {
				System.out.println(
						"Student: " + res1.getString(1) + " | " + res1.getString(2) + " | " + res1.getString(3));
				while (res2.next())
					System.out.println("Course code: " + res2.getString(1) + " | Grade: " + res2.getDouble(3)
							+ " | Title: " + res2.getString(2));
			} else
				System.out.println("Student doesn't exist.");
			res1.close();
			res2.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertLabGroupsByCourse(String cCode, int moduleNum, int groupNum, int sNumber) {
		Random rand = new Random();

		try {
			PreparedStatement pst = conn.prepareStatement(
					"INSERT INTO \"LabModule\" VALUES (?, ?, (SELECT MAX(module_no)+1 FROM \"LabModule\"), ?::labmodule_type, ?, ?, 10)");

			for (int i = 0; i < moduleNum; i++) {
				if (i > Math.round(moduleNum * 0.2))
					pst.setString(3, "lab_exercise");
				else
					pst.setString(3, "project");
				pst.setString(1, cCode);
				pst.setInt(2, sNumber);
				pst.setString(4, "Title " + i);
				pst.setInt(5, (int) Math.floor(Math.random() * (5) + 2));

				pst.executeUpdate();

				PreparedStatement pst1 = conn.prepareStatement(
						"INSERT INTO \"Workgroup\" VALUES (?,?,(SELECT MAX(module_no) FROM \"LabModule\") , (SELECT MAX(\"wgID\")+1 FROM \"Workgroup\"), ?)");
				for (int j = 0; j < groupNum; j++) {
					pst1.setString(1, cCode);
					pst1.setInt(2, sNumber);

					int r;

					do {
						r = (int) Math.round(rand.nextGaussian() * 2 + 6);
					} while (r < 0 || r > 10);

					pst1.setDouble(3, r);
					pst1.executeUpdate();

					Statement st = conn.createStatement();
					ResultSet res = st.executeQuery("SELECT \"Register\".amka \r\n" + "FROM \"Register\" \r\n"
							+ "WHERE (serial_number,course_code) IN (SELECT serial_number,course_code \r\n"
							+ "                    				  FROM \"LabModule\" WHERE module_no = (SELECT MAX(module_no) FROM \"LabModule\")) \r\n"
							+ "									  ORDER BY random() \r\n"
							+ "									  LIMIT (SELECT DISTINCT max_members \r\n"
							+ "											 FROM \"LabModule\" \r\n"
							+ "                    						 WHERE module_no = (SELECT MAX(module_no) FROM \"LabModule\"))");

					PreparedStatement pst2 = conn.prepareStatement(
							"INSERT INTO \"Joins\" VALUES (?, ?, ?,(SELECT MAX(module_no) FROM \"LabModule\"),(SELECT MAX(\"wgID\") FROM \"Workgroup\"))");
					while (res.next()) {
						pst2.setString(1, res.getString(1));
						pst2.setString(2, cCode);
						pst2.setInt(3, sNumber);

						pst2.executeUpdate();
					}
					res.close();
					pst2.close();
				}
				pst1.close();
			}
			pst.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void insertLabGroupsByField(String field, int moduleNum, int groupNum, int sNumber) {
		try {
			PreparedStatement pst = conn.prepareStatement("SELECT course_code FROM \"CourseRun\" \r\n"
					+ "WHERE LEFT(course_code, 3) = ?\r\n" + "AND labuses IS NOT NULL\r\n" + "GROUP BY course_code");

			pst.setString(1, field);
			ResultSet res = pst.executeQuery();

			while (res.next()) {
				insertLabGroupsByCourse(res.getString(1), moduleNum, groupNum, sNumber);
			}

			res.close();
			pst.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void printMenu() {
		System.out.println("\n---------------------------------Menu--------------------------------\n");
		System.out.println("1. Show student's grades for a specific semester");
		System.out.println("2. Insert random lab modules and workgroups for a specific course");
		System.out.println("3. Insert random lab modules and workgroups for a specific field\n");
		System.out.println("---------------------------------------------------------------------\n");
	}

	public static void main(String[] args) throws ClassNotFoundException {
		DbApp app = new DbApp();
		String ip, dbname, username, password, cCode, field;
		int userOption, moduleNum, groupNum, sNumber;

		System.out.println("Connect to database\n");
		ip = waitForInput("Enter ip: ");
		dbname = waitForInput("Enter database name: ");
		username = waitForInput("Enter username: ");
		password = waitForInput("Enter password: ");
		app.dbConnect(ip, dbname, username, password);

		do {
			app.printMenu();
			userOption = Integer.parseInt(waitForInput("Enter your option: "));

			switch (userOption) {

			case 1:
				app.showGrades();
				break;

			case 2:
				cCode = waitForInput("Enter course code: ");
				moduleNum = Integer.parseInt(waitForInput("Enter number of lab modules: "));
				groupNum = Integer.parseInt(waitForInput("Enter number of groups: "));
				sNumber = Integer.parseInt(waitForInput("Enter serial number: "));
				app.insertLabGroupsByCourse(cCode, moduleNum, groupNum, sNumber);
				break;

			case 3:
				field = waitForInput("Enter field: ");
				moduleNum = Integer.parseInt(waitForInput("Enter number of lab modules: "));
				groupNum = Integer.parseInt(waitForInput("Enter number of groups: "));
				sNumber = Integer.parseInt(waitForInput("Enter serial number: "));
				app.insertLabGroupsByField(field, moduleNum, groupNum, sNumber);
				break;
			}

		} while (userOption >= 1 && userOption <= 3);

		app.dbClose();

	}
}
