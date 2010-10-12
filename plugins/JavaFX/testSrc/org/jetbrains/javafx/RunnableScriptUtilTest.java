package org.jetbrains.javafx;

import org.jetbrains.javafx.run.RunnableScriptUtil;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class RunnableScriptUtilTest extends JavaFxLightFixtureTestCase {
  public void doTest(boolean expected) throws Exception {
    final String testPath = "/run/";
    myFixture.configureByFile(testPath + getTestName(false) + ".fx");
    final boolean result = RunnableScriptUtil.isRunnable(myFixture.getFile());
    assertSame(expected, result);
  }

  public void testRun() throws Exception {
    doTest(true);
  }

  public void testStage() throws Exception {
    doTest(true);
  }

  public void testQualifiedStage() throws Exception {
    doTest(true);
  }

  public void testNonRunnable() throws Exception {
    doTest(false);
  }

  public void testVariable() throws Exception {
    doTest(true);
  }

  public void testVariable2() throws Exception {
    doTest(false);
  }

  public void testAssignment() throws Exception {
    doTest(true);
  }
}
