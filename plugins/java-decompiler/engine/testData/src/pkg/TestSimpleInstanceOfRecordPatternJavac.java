package pkg;

public class TestSimpleInstanceOfRecordPatternJavac {
  public static void main(String[] args) {

  }

  public static void instanceOfTest1(Object o) {
    if (o instanceof R(Object s1)) {
      System.out.println(s1);
    }
    System.out.println("1");
  }

  public static void tryInstanceOfTest1(Object o) {
    int a = 1;
    try {
      if (o instanceof R(String s1)) {
        a += 34;
      }
    } catch (Exception e) {
      if (o instanceof R(String s1)) {
        a += 34;
      }
    }
  }

  public static void negativeInstanceOfTest1(Object o) {
    if (!(o instanceof R(Object s1))) {
      return;
    } else if (s1.hashCode() == 1) {
      System.out.println(s1);
      System.out.println("1");
    }
  }

  public static void instanceOfTest2(Object o) {
    if (o instanceof R(String s1)) {
      System.out.println(s1);
      if (s1.hashCode() == 1) {
        System.out.println("1");
      }
    }
    System.out.println("5");
  }

  public static void instanceOfTest3(Object o) {
    if (o instanceof R(String s1)) {
      if (s1.isEmpty()) {
        System.out.println("111");
        System.out.println("111");
        System.out.println("111");
        System.out.println("111");
      }
    }
    System.out.println("s222222222222");
    System.out.println("s222222222222");
  }

  public static void instanceOfTest4(Object o) {
    if (o.hashCode() == 1) {
      if (o instanceof R(String s1)) {
        if (o instanceof R(String s2)) {
          if (s1.isEmpty()) {
            System.out.println("111");
            System.out.println("111");
            System.out.println("111");
            System.out.println("111");
          }
        }
      }
    }
    System.out.println("s222222222222");
    System.out.println("s222222222222");
  }

  public static void instanceOfTestDouble1(Object o, Object o2) {
    if (o instanceof R(Object s1)) {
      System.out.println(s1);
    }

    if (o2 instanceof R(Object r)) {
      System.out.println(r);
    }
    System.out.println("s2222222");
  }

  public static void instanceOfTestDouble2(Object o, Object o2) {
    if (o instanceof R(Object s1)) {
      System.out.println(s1);
    }

    if (o2 instanceof R(String r)) {
      System.out.println(r);
    }
    System.out.println("2222222");
  }

  public static void instanceOfTestDoubleNegate2(Object o, Object o2) {
    if (!(o instanceof R(Object s1))) {
      return;
    }
    System.out.println(s1);

    if (!(o2 instanceof R(String r))) {
      return;
    }
    System.out.println(r);

    System.out.println("2222222");
  }

  record R(Object o) {
  }
}
