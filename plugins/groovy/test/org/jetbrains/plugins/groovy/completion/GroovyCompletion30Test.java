// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
@RegistryKey(key = MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE, value = "true")
public class GroovyCompletion30Test {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @AfterEach
  public void tearDown() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
  }

  @Test
  public void testInferArgumentTypeFromClosure() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def foo(a, closure) {
                      closure(a)
                    }
                  
                    foo(1) { it.byt<caret> }
                    """, """
                    def foo(a, closure) {
                      closure(a)
                    }
                  
                    foo(1) { it.byteValue()<caret> }
                    """);
    });
  }

  @Test
  public void testInferArgumentTypeFromLambda() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def foo(a, closure) {
                      closure(a)
                    }
                  
                    foo(1, (it) -> it.byt<caret> )
                    """, """
                    def foo(a, closure) {
                      closure(a)
                    }
                  
                    foo(1, (it) -> it.byteValue()<caret> )
                    """);
    });
  }

  @Test
  public void testInferArgumentTypeFromClosureInsideClass() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                  
                    class K {
                      def foo(a, closure) {
                        closure(a)
                      }
                  
                      def bar() {
                        foo(1) { it.byt<caret> }
                      }
                    }
                    """, """
                  
                    class K {
                      def foo(a, closure) {
                        closure(a)
                      }
                  
                      def bar() {
                        foo(1) { it.byteValue()<caret> }
                      }
                    }
                    """);
    });
  }

  @Test
  public void testInferArgumentTypeForClosure() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                  
                    def foo(a, b) { b(a) }
                  
                    foo(1) { a -> a.byteValue() }
                    foo('q') { it.len<caret>}
                    """, """
                  
                    def foo(a, b) { b(a) }
                  
                    foo(1) { a -> a.byteValue() }
                    foo('q') { it.length()<caret>}
                    """);
    });
  }

  @Test
  public void testInferArgumentTypeFromMethod() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def foo(a) {
                      a.byt<caret>
                    }
                  
                    foo 1
                    """, """
                    def foo(a) {
                      a.byteValue()<caret>
                    }
                  
                    foo 1
                    """);
    });
  }

  @Test
  public void testCompletionAfterComplexStatement() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                  
                    def x(List<Integer> l){}
                  
                    void m(l) {
                        x([l])
                        l.by<caret>
                    }""", """
                  
                    def x(List<Integer> l){}
                  
                    void m(l) {
                        x([l])
                        l.byteValue()<caret>
                    }""");
    });
  }

  @Test
  public void testCompleteQuotedMethodName() {
    runInEdtAndWait(() -> {
      completion.get().doBasicTest("""
                    def 'good job'() {}
                    goo<caret>
                    """,
                  """
                    def 'good job'() {}
                    'good job'()
                    """);
    });
  }

}
