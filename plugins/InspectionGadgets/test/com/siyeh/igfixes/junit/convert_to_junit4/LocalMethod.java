
import junit.framework.TestCase;

public class LocalMethod extends TestCase {

  @org.junit.Test
  public void <caret>testForeign() {
    what();
  }

  void what() {}
}