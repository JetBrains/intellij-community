import org.junit.*;

@Ignore("for good reason")
public class IgnoredJUnitTest {

  @<warning descr="Test method 'testThis()' annotated with 'Ignore'">Ignore</warning>
  @Test
  public void testThis() {}
}