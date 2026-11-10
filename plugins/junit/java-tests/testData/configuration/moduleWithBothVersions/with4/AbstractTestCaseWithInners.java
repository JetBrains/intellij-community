package with4;

import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

@RunWith(JUnit38ClassRunner.class)
public abstract class AbstractTestCaseWithInners extends junit.framework.TestCase {
  @RunWith(JUnit38ClassRunner.class)
  public static class Inner extends AbstractTestCaseWithInners {
    public void test1() {}
  }
}
