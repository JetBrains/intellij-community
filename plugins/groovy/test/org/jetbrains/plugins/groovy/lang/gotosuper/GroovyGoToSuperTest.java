package org.jetbrains.plugins.groovy.lang.gotosuper;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyGoToSuperTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/gotoSuper/";
  }

  public void testGts1() throws Throwable { doTest(); }
  public void testGts2() throws Throwable { doTest(); }
  public void testGts3() throws Throwable { doTest(); }

  private void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(GroovyFileType.GROOVY_LANGUAGE);
    handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());

    myFixture.checkResult(data.get(1));
  }

}