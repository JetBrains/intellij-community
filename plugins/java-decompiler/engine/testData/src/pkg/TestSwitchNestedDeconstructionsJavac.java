package pkg;

public class TestSwitchNestedDeconstructionsJavac {
  public static void main(String[] args) {

  }

  record R1(Object o) {

  }


  public static void testNestedSwitches(Object o) {
    switch (o) {
      case R2(String s, String s2) when s.length() > 123456789 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, String s2) when s.length() > 2 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, Object s2) when s.length() > 45 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) when s.hashCode() > 7 -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case Object ob -> System.out.println(o);
    }
  }

  public static void testStringString(Object o) {
    switch (o) {
      case R2(String s1, String s2) -> {
        if (s2.isEmpty()) {
          System.out.println("3");
        }
      }
      default -> System.out.println("3");
    }
    System.out.println("1");
  }

  public static void testNestedLevel2(Object o) {
    switch (o) {
      case R2(String s, String s2) when s2.length() > 11 -> {
        if (s.length() == 9) {
          System.out.println(o);
        }
      }
      case R2(Object s, R1(String s2)) when s2.length() > 7 -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case Object ob -> System.out.println(o);
    }
  }

  public static void testNumberString(Object o) {
    switch (o) {
      case R2(String s, String s2) when s2.length() > 10 -> {
        if (s.length() == 9) {
          System.out.println(o);
        }
      }
      case R2(Number s, String s2) when s2.length() > 9 -> {
        if (s.hashCode() == 9) {
          System.out.println(o);
        }
      }
      case R2(String s, Object s2) when s.length() > 7 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case Object ob -> System.out.println(o);
    }
  }

  public static void test2DeepDeconstruction(Object o) {
    switch (o) {
      case R1(R1(String s)) when s.hashCode() == 5 -> {
        System.out.println("123456789");
      }
      case R1(String s) when s.hashCode() == 3 -> {
        System.out.println("3");
      }
      default -> System.out.println("3");
    }
    System.out.println("1");
  }

  public static void testDoubleLongCase(Object o) {
    switch (o) {
      case R2(String s, String s2) when s.length() > 3 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, String s2) when s.length() > 4 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, Object s2) when s.length() > 3 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) when s.hashCode() > 3 -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case Object ob -> System.out.println(o);
    }

    switch (o) {
      case R2(String s, String s2) when s.length() > 3 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, String s2) when s.length() > 4 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(String s, Object s2) when s.length() > 3 -> {
        if (s.length() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) when s.hashCode() > 3 -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case R2(Object s, Object s2) -> {
        if (s.hashCode() == 2) {
          System.out.println(o);
        }
      }
      case Object ob -> System.out.println(o);
    }
  }

  record R2(Object o, Object o2) {

  }
}