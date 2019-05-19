import junit.framework.*;
import org.junit.Assert;
import org.junit.Test;

public class Inheritance {

  @Test
  public void testBla() {
    Assert.assertFalse("message", false);
  }

  @org.junit.Test
  public void testForeign() {

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