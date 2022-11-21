public class AlwaysInverted {

  public class A {

    public static boolean isAlwaysInverted() {
      return Math.random() < .5;
    }
  }

  public class B extends A {

    public void foo() {
      if (super.isAlwaysInverted()) System.out.println("foo");
    }

    public void bar() {
      if (super.isAlwaysInverted()) System.out.println("bar");
    }
  }
}