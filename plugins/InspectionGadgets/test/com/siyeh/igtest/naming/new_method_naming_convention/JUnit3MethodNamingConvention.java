public class JUnit3MethodNamingConvention extends junit.framework.TestCase {

  public void <warning descr="JUnit 3 test method name 'testA' is too short (5 < 8)">testA</warning>() {}

  public void <warning descr="JUnit 3 test method name 'testAbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz' is too long (82 > 64)">testAbcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz</warning>() {}

  public void <warning descr="JUnit 3 test method name 'testGiveMeMore$$$' doesn't match regex 'test[A-Za-z_\d]*'">testGiveMeMore$$$</warning>() {}

  public void test_me_properly() {}
}