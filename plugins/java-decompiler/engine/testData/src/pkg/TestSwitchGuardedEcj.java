package pkg;

public class TestSwitchGuardedEcj {

  public static void main(String[] args) {

  }

  public static void testObject(Object o) {
    switch (o) {
      case String s when s.isEmpty() && s.getBytes().length == 2 -> System.out.println("empty s");
      case String s -> System.out.println("s");
      case Integer i -> System.out.println("i");
      case Object ob -> System.out.println(o);
    }
    System.out.println("1");
  }

  public static void testObject2(Object o) {
    switch (o) {
      case String s when s.isEmpty() && s.getBytes().length == 2 -> System.out.println("empty s");
      case String s -> System.out.println("s");
      case Integer i -> System.out.println("ii");
      case Object ob -> System.out.println(o);
    }
  }

  public static void testObject3(Object o) {
    TASK:
    while (o.hashCode() == 1) {
      switch (o) {
        case String s when s.isEmpty() && s.getBytes().length == 2:
          System.out.println("empty s");
          break TASK;
        case String s:
          System.out.println("s");
          continue TASK;
        case Integer i:
          System.out.println("i");
          break;
        case Object ob:
          System.out.println(o);
          break;
      }
      break;
    }
    System.out.println("1");
  }
}
