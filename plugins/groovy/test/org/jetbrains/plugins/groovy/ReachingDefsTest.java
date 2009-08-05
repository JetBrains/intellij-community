package org.jetbrains.plugins.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;

import java.util.List;

/**
 * @auther ven
 */
public class ReachingDefsTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testData/groovy/reachingDefs/";
  }

  public void testAssign() throws Throwable { doTest(); }
  public void testClosure() throws Throwable { doTest(); }
  public void testClosure1() throws Throwable { doTest(); }
  public void testEm1() throws Throwable { doTest(); }
  public void testEm2() throws Throwable { doTest(); }
  public void testEm3() throws Throwable { doTest(); }
  public void testIf1() throws Throwable { doTest(); }
  public void testInner() throws Throwable { doTest(); }
  public void testLocal1() throws Throwable { doTest(); }
  public void testLocal2() throws Throwable { doTest(); }
  public void testSimpl1() throws Throwable { doTest(); }
  public void testSimpl2() throws Throwable { doTest(); }
  public void testSimpl3() throws Throwable { doTest(); }
  public void testWhile1() throws Throwable { doTest(); }

  public void doTest() throws Exception {
    final List<String> data = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + getTestName(true) + ".test");
    String text = data.get(0);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);

    int selStart = myFixture.getEditor().getSelectionModel().getSelectionStart();
    int selEnd = myFixture.getEditor().getSelectionModel().getSelectionEnd();

    final GroovyFile file = (GroovyFile) myFixture.getFile();
    final PsiElement start = file.findElementAt(selStart);
    final PsiElement end = file.findElementAt(selEnd - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    assert owner != null;
    GrStatement firstStatement = getStatement(start, owner);
    GrStatement lastStatement = getStatement(end, owner);
    final FragmentVariableInfos fragmentVariableInfos = ReachingDefinitionsCollector.obtainVariableFlowInformation(firstStatement, lastStatement);
    assertEquals(data.get(1), dumpInfo(fragmentVariableInfos).trim());
  }

  private static String dumpInfo(FragmentVariableInfos fragmentVariableInfos) {
    StringBuilder builder = new StringBuilder();
    builder.append("input:\n");
    for (VariableInfo info : fragmentVariableInfos.getInputVariableNames()) {
      builder.append(info.getName()).append("\n");
    }

    builder.append("output:\n");
    for (VariableInfo info : fragmentVariableInfos.getOutputVariableNames()) {
      builder.append(info.getName()).append("\n");
    }

    return builder.toString();
  }

  private static GrStatement getStatement(@NotNull PsiElement element, PsiElement context) {
    while (element.getParent() != context) {
      element = element.getParent();
      assert element != null;
    }

    return (GrStatement) element;
  }

}
