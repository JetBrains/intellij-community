import org.junit.Test;

public class <warning descr="Test case 'UnconstructableJUnit4TestCase' is not constructable by most test runners">UnconstructableJUnit4TestCase</warning> {

  public UnconstructableJUnit4TestCase(String s) {}
  public UnconstructableJUnit4TestCase() {}

  @Test
  void testMe() {}
}