package pkg;

public class TestSwitchGuarded2Javac {

  public static void main(String[] args) {
    testObject0(4);
  }

  public static void testObject0(Object o) {
    try {
      System.out.println("inside try 2");
      switch (o) {
        case Integer n when n > 3 -> {
          System.out.println("4");
          throw new RuntimeException();
        }
        case Object ob -> System.out.println(o);
      }
      System.out.println("2");
    } catch (UnsupportedOperationException e) {
      System.out.println("exception");
    } finally {
      System.out.println("finally");
    }
  }

  public static void testObject1(Object o) {
    System.out.println("2");
    switch (o) {
      case Integer n when n > 1 -> {
        if (n == 1) {
          System.out.println("212");
        }
        System.out.println(2);
        System.out.println(1);
      }
      case Integer n when n > 2 -> {
        if (n == 1) {
          System.out.println("4");
        }
      }
      case Integer n when n > 3 -> {
        System.out.println("4");
      }
      case Object ob -> System.out.println(o);
    }
    System.out.println("2");
    System.out.println("2");
    switch (o) {
      case Integer n when n > 1 -> {
        if (n == 1) {
          System.out.println("212");
        }
        System.out.println(2);
        System.out.println(1);
      }
      case Integer n when n > 2 -> {
        if (n == 1) {
          System.out.println("4");
        }
      }
      case Integer n when n > 3 -> {
        System.out.println("4");
      }
      case Object ob -> System.out.println(o);
    }
    System.out.println("2");
  }


  public static void testObject2(Object o) {
    switch (o) {
      case Integer n -> {
        if (n == 1) {
          System.out.println("2");
        }
        System.out.println(1);
        System.out.println(1);
      }
      case Object ob -> System.out.println(o);
    }
    System.out.println("2");
    switch (o) {
      case Integer n -> {
        if (n == 1) {
          System.out.println("1");
        }
        System.out.println(1);
        System.out.println(1);
      }
      case Object ob -> System.out.println(o);
    }
    System.out.println("2");
  }

  public static void testObject3(Object o) {
    System.out.println("2");
    switch (o) {
      case Integer n when n > 1 -> {
        if (n == 1) {
          System.out.println("212");
        }
        System.out.println(2);
        System.out.println(1);
      }
      case Integer n when n > 2 -> {
        if (n == 1) {
          System.out.println("4");
        }
      }
      case Integer n when n > 3 -> {
        System.out.println("4");
      }
      case Object ob -> System.out.println(o);
    }
    System.out.println("2");
  }
}
