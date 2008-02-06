package org.jetbrains.plugins.groovy.lang.dataFlow.reachingDefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @auther ven
 */
public class ReachingDefsTest extends SimpleGroovyFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/dataFlow/reachingDefs/data/";

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

    final GroovyFile file = (GroovyFile) TestUtils.createPseudoPhysicalGroovyFile(myProject, text);
    final PsiElement start = file.findElementAt(selStart);
    final PsiElement end = file.findElementAt(selEnd - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    assert owner != null;
    GrStatement firstStatement = getStatement(start, owner);
    GrStatement lastStatement = getStatement(end, owner);
    final FragmentVariableInfos fragmentVariableInfos = ReachingDefinitionsCollector.obtainVariableFlowInformation(firstStatement, lastStatement);
    return dumpInfo(fragmentVariableInfos);
  }

  private String dumpInfo(FragmentVariableInfos fragmentVariableInfos) {
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

  private GrStatement getStatement(@NotNull PsiElement element, PsiElement context) {
    while (element.getParent() != context) {
      element = element.getParent();
      assert element != null;
    }

    return (GrStatement) element;
  }

  public static Test suite() {
    return new ReachingDefsTest();
  }
}
