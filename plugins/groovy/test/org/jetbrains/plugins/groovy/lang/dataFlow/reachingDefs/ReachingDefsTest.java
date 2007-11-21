package org.jetbrains.plugins.groovy.lang.dataFlow.reachingDefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @auther ven
 */
public class ReachingDefsTest extends SimpleGroovyFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/dataFlow/reachingDefs/data/";

  private static final String ourSelectionStartMarker = "<selection>";
  private static final String ourSelectionEndMarker = "</selection>";

  public ReachingDefsTest() {
    super(DATA_PATH);
  }

  public String transform(String testName, String[] data) throws Exception {
    String text = data[0];
    int selStart = Math.max(text.indexOf(ourSelectionStartMarker), 0);
    text = withoutText(text, ourSelectionStartMarker);
    int selEnd = text.indexOf(ourSelectionEndMarker);
    if (selEnd < 0) selEnd = text.length();
    text = withoutText(text, ourSelectionEndMarker);
    assert (selStart >= 0) && (selStart >= 0) || ((selStart < 0) && (selStart < 0));

    final GroovyFile file = (GroovyFile) TestUtils.createPseudoPhysicalFile(myProject, text);
    final PsiElement start = file.findElementAt(selStart);
    final PsiElement end = file.findElementAt(selEnd - 1);
    final PsiElement parent = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    assert parent != null;
    GrStatement firstStatement = getStatement(start, parent);
    GrStatement lastStatement = getStatement(end, parent);
    final VariableInfo variableInfo = ReachingDefinitionsCollector.obtainVariableFlowInformation(firstStatement, lastStatement);
    return dumpInfo(variableInfo);
  }

  private String dumpInfo(VariableInfo variableInfo) {
    StringBuilder builder = new StringBuilder();
    builder.append("input:\n");
    for (String varName : variableInfo.getInputVariableNames()) {
      builder.append(varName).append("\n");
    }

    builder.append("output:\n");
    for (String varName : variableInfo.getOutputVariableNames()) {
      builder.append(varName).append("\n");
    }

    return builder.toString();
  }

  private GrStatement getStatement(@NotNull PsiElement element, PsiElement context) {
    while (element.getParent() != context) {
      element = element.getParent();
      assert element != null;
    }

    return (GrStatement) element;
  }

  private String withoutText(String text, String toCut) {
    final int i = text.indexOf(toCut);
    if (i >= 0) {
      StringBuilder builder = new StringBuilder();
      builder.append(text.substring(0, i));
      builder.append(text.substring(i + toCut.length()));
      return builder.toString();
    }
    return text;
  }

  public static Test suite() {
    return new ReachingDefsTest();
  }
}
