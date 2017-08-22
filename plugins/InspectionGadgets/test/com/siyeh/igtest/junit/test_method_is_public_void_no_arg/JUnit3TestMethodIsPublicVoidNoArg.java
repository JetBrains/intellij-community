public class JUnit3TestMethodIsPublicVoidNoArg extends junit.framework.TestCase {

  public JUnit3TestMethodIsPublicVoidNoArg() {}

  void <warning descr="Test method 'testOne()' is not declared 'public void'">testOne</warning>() {}

  public int <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>() {
    return 2;
  }

  public static void <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() {}

  public void <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(int i) {}

  public void testFive() {}
  
  //ignore when method doesn't look like test anymore
  void testSix(int i) {}
}