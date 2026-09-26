package pkg;

public class TestSwitchRules {
  public static void main(String[] args) {
    test1("1");
    test2("1");
    test3("1");
  }


  private static void test1(String r2) {
    switch (r2) {
      case "2" -> System.out.println("4");
      case "3" -> System.out.println("2");
      default -> System.out.println("31");
    }
  }

  private static void test2(String r2) {
    switch (r2) {
      case "2":
        break;
      default:
        System.out.println("31");
        break;
      case "3":
        System.out.println("2");
        break;
    }
  }

  private static void test3(String r2) {
    switch (r2) {
      case "2":
      default:
        System.out.println("31");
        break;
      case "3":
        System.out.println("2");
        break;
    }
  }
}