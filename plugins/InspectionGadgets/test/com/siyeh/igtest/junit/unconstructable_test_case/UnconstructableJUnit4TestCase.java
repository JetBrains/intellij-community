import org.junit.Test;

public class <warning descr="Test class 'UnconstructableJUnit4TestCase' is not constructable because it has an incompatible constructor">UnconstructableJUnit4TestCase</warning> {

  public UnconstructableJUnit4TestCase(String s) {}
  public UnconstructableJUnit4TestCase() {}

  @Test
  void testMe() {}
}