import junit.framework.*;

public class Inheritance extends TestCase {

  public void testBla() {
    assertFalse("message", false);
  }

  @org.junit.Test
  public void <caret>testForeign() {

  }

  static class Offspring extends Inheritance {}

  public static void collect() {
    Offspring inheritance = new Offspring(); // expected type Offspring
    System.out.println("inheritance = " + inheritance);

    Inheritance inheritance2 = new Inheritance(); // expected type Inheritance
    System.out.println("inheritance2 = " + inheritance2);

    System.out.println(new Inheritance()); // expected type object
  }
}