class Shape {}
class Triangle  extends Shape { int calculateArea() { return 169660; } }
  record Point(int i, int j) {}
enum Color { RED, GREEN, BLUE; }


class Foo {
  void foo1(Object o) {
    switch (o) {
      case Integer i -> System.out.printf("int %d", i);
      case Long l -> System.out.printf("long %d", l);
      case Double d -> System.out.printf("double %f", d);
      case String s -> System.out.printf("String %s", s);
      case Triangle t && (t.calculateArea() > 100) -> System.out.println("Large triangle");
      case Triangle t, null -> System.out.println("Small triangle or null");
      case Color c -> System.out.println("Color with " + Color.values().length + " values");
      case Point p -> System.out.println("Record class: " + "blah blah blah");
      case int[] ia -> System.out.println("Array of ints of length" + ia.length);
      default -> System.out.println("Non-triangle");
    }
  }

  void foo2(Object o) {
    switch (o) {
      case Integer i:
        System.out.printf("int %d", i);
        break;
      case Long l:
        System.out.printf("long %d", l);
        break;
      case Double d:
        System.out.printf("double %f", d);
        break;
      case String s:
        System.out.printf("String %s", s);
        break;
      case Triangle t && (t.calculateArea() > 100):
        System.out.println("Large triangle");
        break;
      case Triangle t, null:
        System.out.println("Small triangle or null");
        break;
      case Color c:
        System.out.println("Color with " + Color.values().length + " values");
        break;
      case Point p:
        System.out.println("Record class: " + "blah blah blah");
        break;
      case int[] ia:
        System.out.println("Array of ints of length" + ia.length);
        break;
      default:
        System.out.println("Non-triangle");
    }
  }

  int foo3(Integer i) {
    return switch (i) {
      case 1, 2, 3, 4, 5, null, default -> 42;
      case 42 -> 666;
    };
  }

  int foo4(Integer i) {
    return switch (i) {
      default -> 42;
    };
  }

  int foo5(Integer i) {
    return switch (i) {
      default -> 42;
    };
  }
}

class Bar extends Foo {
  void <warning descr="Method 'foo1()' is identical to its super method">foo1</warning>(Object o) {
    switch (o) {
      case ((Integer i)) -> System.out.printf("int %d", i);
      case Long blahBlahBlah -> System.out.printf("long %d", blahBlahBlah);
      case Double d -> System.out.printf("double %f", d);
      case String ssssss -> System.out.printf("String %s", ssssss);
      case ((((Triangle t)) && ((t.calculateArea() > 100)))) -> System.out.println("Large triangle");
      case null, Triangle t -> System.out.println("Small triangle or null");
      case Color c -> System.out.println("Color with " + Color.values().length + " values");
      case Point p -> System.out.println("Record class: " + "blah blah blah");
      case int[] ia -> System.out.println("Array of ints of length" + ia.length);
      case default -> System.out.println("Non-triangle");
    }
  }

  void <warning descr="Method 'foo2()' is identical to its super method">foo2</warning>(Object o) {
    switch (o) {
      case ((Integer i)):
        System.out.printf("int %d", i);
        break;
      case Long blahBlahBlah:
        System.out.printf("long %d", blahBlahBlah);
        break;
      case Double d:
        System.out.printf("double %f", d);
        break;
      case String ssssss:
        System.out.printf("String %s", ssssss);
        break;
      case ((((Triangle t)) && ((t.calculateArea() > 100)))):
        System.out.println("Large triangle");
        break;
      case null, Triangle t:
        System.out.println("Small triangle or null");
        break;
      case Color c:
        System.out.println("Color with " + Color.values().length + " values");
        break;
      case Point p:
        System.out.println("Record class: " + "blah blah blah");
        break;
      case int[] ia:
        System.out.println("Array of ints of length" + ia.length);
        break;
      case default:
        System.out.println("Non-triangle");
    }
  }

  int <warning descr="Method 'foo3()' is identical to its super method">foo3</warning>(Integer i) {
    return switch (i) {
      case null, 4, default, 1, 5, 3, 2 -> 42;
      case 42 -> 666;
    };
  }

  int foo4(Integer i) {
    return switch (i) {
      case default -> 13;
    };
  }

  int foo5(Integer i) {
    return switch (i) {
      case 42, default -> 42;
    };
  }
}