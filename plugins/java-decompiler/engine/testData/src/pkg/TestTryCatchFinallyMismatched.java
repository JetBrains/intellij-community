package pkg;

public class TestTryCatchFinallyMismatched {
  public static void main(String[] args) {

  }

  public int test(String a) {
    try {
      return Integer.parseInt(a);
    } catch (Exception e) {
      System.out.println("Error" + e);
    } finally {
      System.out.println("Finally");
      System.out.println("Finally");
    }
    return -1;
  }

  public int test2(String a) {
    try {
      return Integer.parseInt(a);
    } finally {
      System.out.println("Finally");
      System.out.println("Finally");
    }
  }

  public int test3(String a) {
    int a2 = 1;
    try {
      return Integer.parseInt(a);
    } catch (Exception e) {
      System.out.println("Error" + e);
    } finally {
      a2 = 3 + a.length();
      System.out.println("Finally");
      System.out.println("Finally");
    }
    return a2;
  }

  public int test4(String a) {
    int a2 = 1;
    try {
      return Integer.parseInt(a);
    } catch (Exception e) {
      System.out.println("Error" + e);
    } finally {
      String test = "test";
      System.out.println("Finally" + test);
      System.out.println("Finally");
    }
    return a2;
  }

}
