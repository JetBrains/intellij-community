// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GroovyCompletion40Test {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @AfterEach
  public void tearDown() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
  }

  @Test
  public void test_basic() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def x = sw<caret>
                    """, """
                    def x = switch (<caret>)
                    """);
    });
  }

  @Test
  public void test_case_inside_switch_expression() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def x = switch (10) {
                      ca<caret>
                    }
                    """, """
                    def x = switch (10) {
                      case <caret>
                    }
                    """);
    });
  }

  @Test
  public void test_in_switch_block() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                  
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
    });
  }

}
