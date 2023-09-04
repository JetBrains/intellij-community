package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyGotoDeclarationTest extends LightGroovyTestCase {
  private void doTest() {
    final String name = getTestName();
    getFixture().configureByFile(name + ".groovy");
    getFixture().performEditorAction("GotoDeclaration");
    getFixture().checkResultByFile(name + "_after.groovy");
  }

  public void testDefaultConstructor() {
    doTest();
  }

  public void testQualifierInNew() {
    doTest();
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final String basePath = TestUtils.getTestDataPath() + "gotoDeclaration/";
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
