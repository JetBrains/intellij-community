package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class CodeBlockGenerationJava14Test extends CodeBlockGenerationBaseTest {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    // text blocks are not yet supported there
    return JAVA_14;
  }

  public void testMultilineStringOldJava() {
    doTest();
  }
}
