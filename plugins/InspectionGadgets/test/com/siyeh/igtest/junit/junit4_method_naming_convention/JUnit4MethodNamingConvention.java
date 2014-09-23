import org.junit.Test;

public class JUnit4MethodNamingConvention {

  @Test
  public void <warning descr="JUnit 4 test method name 'a' is too short (1 < 4)">a</warning>() {}

  @Test
  public void <warning descr="JUnit 4 test method name 'abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz' is too long (78 > 64)">abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz</warning>() {}

  @Test
  public void <warning descr="JUnit 4 test method name 'more$$$' doesn't match regex '[a-z][A-Za-z_\d]*'">more$$$</warning>() {}

  @Test
  public void assure_foo_is_never_null() {}
}