import org.junit.Test;

public class JUnit4TestMethodIsPublicVoidNoArg {

  @Test
  JUnit4TestMethodIsPublicVoidNoArg() {}

  @Test
  void <warning descr="Test method 'testOne()' is not declared 'public void'">testOne</warning>() {}

  @Test
  public int <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>() {
    return 2;
  }

  @Test
  public static void <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() {}

  @Test
  public void <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(int i) {}

  @Test
  public void testFive() {}

  @Test
  public void testMock(@mockit.Mocked String s) {}
}