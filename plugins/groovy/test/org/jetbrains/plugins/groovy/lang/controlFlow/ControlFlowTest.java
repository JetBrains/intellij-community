package org.jetbrains.plugins.groovy.lang.controlFlow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ControlFlowTest extends SimpleGroovyFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = PathUtil.getDataPath(ControlFlowTest.class);

  public ControlFlowTest() {
    super(DATA_PATH);
  }

  private String dumpControlFlow(Instruction[] instructions) {
    StringBuilder builder = new StringBuilder();
    for (Instruction instruction : instructions) {
      builder.append(instruction.toString()).append("\n");
    }

    return builder.toString();
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
    final Instruction[] instructions = new ControlFlowBuilder().buildControlFlow(owner, null, null);
    return dumpControlFlow(instructions);
  }

  public static Test suite() {
    return new ControlFlowTest();
  }
}
