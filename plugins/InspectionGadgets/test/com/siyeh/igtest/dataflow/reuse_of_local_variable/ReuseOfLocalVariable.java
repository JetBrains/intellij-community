class ReuseOfLocalVariable {

  void x(boolean skipping) {
    boolean curSkipping = skipping;
    if (true) {
      if (false) curSkipping = true;
      else if (true) curSkipping = false;
      if (curSkipping && !skipping) {
      }
    }
  }


  void x() {
    String s = "one";
    System.out.println("s = " + s);
    <warning descr="Reuse of local variable 's'">s</warning> = "two";
    System.out.println("s = " + s);
  }

  void test1() {
    String a = System.getProperty("a");

    if (a == null) {
      a = "default";
    }

    System.out.println(a);
  }

  void test2() {
    String a = System.getProperty("a");

    if (Math.random() > 0.5) {
      if (a == null) {
        a = "default";
      }

      System.out.println(a);
    }
  }

  void test3() {
    int x = 10;
    for (int i = 0; i < 10; i++ ) {
      System.out.println(x);
      x = x + 11;
      System.out.println(x);
    }
  }

  void test4() {
    int x = 1;
    try {
      x = 2;
      System.out.println(x); // what happens when this statement throws an exception?
    }
    catch (Exception e) {
      System.out.println(x);
    }
  }

  void test5() {
    int x = 1;
    if (Math.random() > 0.5) {
      if (Math.random() > 0.5) {
        <warning descr="Reuse of local variable 'x'">x</warning> = 2;
        System.out.println(x);
        return;
      }
      System.out.println(x);
    }
  }
}
class Dummy {
  public static void main(String[] args) {
    int x = 1;
    if (args.length > 0 ) {
      <warning descr="Reuse of local variable 'x'">x</warning> = 2;
      System.out.println("x = "+x);
    } else {
      System.out.println("x = "+x);
    }
  }

  void x() {
    int i = 1;
    if (true) {
      switch (3) {
        case 3:
          i = 2;
          break;
        default:
          i = 3;
          System.out.println(i);
      }
      System.out.println(i);
    }
  }

  void y(int j) {
    int i = 1;
    switch (j) {
      case 1:
        i = 2;
        break;
      case 2:
        return;
    }
    System.out.println(i);
  }
}