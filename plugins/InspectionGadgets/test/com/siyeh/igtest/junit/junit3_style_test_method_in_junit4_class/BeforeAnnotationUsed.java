import org.junit.Before;

public class BeforeAnnotationUsed {

  @Before
  public void before() {}

  public void <warning descr="Old style JUnit test method 'testOldStyle()' in JUnit 4 class">testOldStyle</warning>() {}
}