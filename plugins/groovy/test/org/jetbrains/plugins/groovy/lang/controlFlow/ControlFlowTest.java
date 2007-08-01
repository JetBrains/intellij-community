package org.jetbrains.plugins.groovy.lang.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import junit.framework.Test;

/**
 * @author ven
 */
public class ControlFlowTest extends SimpleGroovyFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/controlFlow/data/";

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
    String methodText = data[0];
    final GroovyFile file = (GroovyFile) TestUtils.createPseudoPhysicalFile(myProject, methodText);
    final Instruction[] instructions = new ControlFlowBuilder().buildControlFlow(file, null, null);
    return dumpControlFlow(instructions);
  }

  public static Test suite() {
    return new ControlFlowTest();
  }
}
