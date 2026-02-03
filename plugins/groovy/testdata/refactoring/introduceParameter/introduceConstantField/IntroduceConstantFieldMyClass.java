class Foo {
  static void func() {
    System.out.println(<selection>MySingleton.INSTANCE</selection>);
  }
}

public class MySingleton {
  public static final MySingleton INSTANCE = new MySingleton();
}
