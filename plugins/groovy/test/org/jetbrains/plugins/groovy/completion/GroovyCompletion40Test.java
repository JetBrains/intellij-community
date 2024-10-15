// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class GroovyCompletion40Test extends GroovyCompletionTestBase {
  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void test_basic() {
    doBasicTest("""
                  def x = sw<caret>
                  """, """
                  def x = switch (<caret>)
                  """);
  }

  public void test_case_inside_switch_expression() {
    doBasicTest("""
                  def x = switch (10) {
                    ca<caret>
                  }
                  """, """
                  def x = switch (10) {
                    case <caret>
                  }
                  """);
  }

  public void test_in_switch_block() {
    doBasicTest("""
                  
                  def x = switch (10) {
                    case 10 -> {
                      yie<caret>
                    }
                  }""", """
                  
                  def x = switch (10) {
                    case 10 -> {
                      yield <caret>
                    }
                  }""");
  }

  @Override
  public final @NotNull DefaultLightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK;
  }
}
