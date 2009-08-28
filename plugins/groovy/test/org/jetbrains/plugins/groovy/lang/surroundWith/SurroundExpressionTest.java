package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithParenthesisExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithTypeCastSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithWithExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfElseExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */
public class SurroundExpressionTest extends LightCodeInsightFixtureTestCase {

  public void testBrackets1() throws Exception { doTest(new GroovyWithParenthesisExprSurrounder()); }
  public void testIf1() throws Exception { doTest(new GroovyWithIfExprSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new GroovyWithIfElseExprSurrounder()); }
  public void testType_cast1() throws Exception { doTest(new GroovyWithTypeCastSurrounder()); }
  public void testWhile1() throws Exception { doTest(new GroovyWithWhileExprSurrounder()); }
  public void testWith2() throws Exception { doTest(new GroovyWithWithExprSurrounder()); }

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/surround/expr/";
  }

  public void doTest(Surrounder surrounder) throws Exception {
    final List<String> data = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    final String fileText = data.get(0);
    myFixture.configureByText("a.groovy", fileText);

    SurroundWithHandler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    PsiUtil.reformatCode(myFixture.getFile());

    myFixture.checkResult(data.get(1));
  }

}
