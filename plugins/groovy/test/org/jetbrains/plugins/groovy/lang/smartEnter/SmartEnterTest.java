package org.jetbrains.plugins.groovy.lang.smartEnter;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.08.2008
 */
public class SmartEnterTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/actions/smartEnter/";
  }

  public void testMethCallComma() throws Throwable { doTest(); }
  public void testMethCallWithArg() throws Throwable { doTest(); }
  public void testMethodCallMissArg() throws Throwable { doTest(); }
  public void testMissBody() throws Throwable { doTest(); }
  public void testMissCondition() throws Throwable { doTest(); }
  public void testMissIfclosureParen() throws Throwable { doTest(); }
  public void testMissIfCurl() throws Throwable { doTest(); }
  public void testMissingIfClosedParenth() throws Throwable { doTest(); }
  public void testMissRParenth() throws Throwable { doTest(); }
  public void testMissRParenthInMethod() throws Throwable { doTest(); }
  public void testMissRQuote() throws Throwable { doTest(); }
  public void testMissRQuoteInCompStr() throws Throwable { doTest(); }

  public void testGotoNextLineInFor() throws Throwable { doTest(); }
  public void testGotoParentInIf() throws Throwable { doTest(); }

  protected static List<SmartEnterProcessor> getSmartProcessors(Language grLanguage) {
    return SmartEnterProcessors.INSTANCE.forKey(grLanguage);
  }

  public void doTest() throws Exception {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));

    final List<SmartEnterProcessor> processors = getSmartProcessors(GroovyFileType.GROOVY_LANGUAGE);
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        final Editor editor = myFixture.getEditor();
        for (SmartEnterProcessor processor : processors) {
          processor.process(getProject(), editor, myFixture.getFile());
        }

      }
    }.execute();
    myFixture.checkResult(data.get(1));
  }

}
