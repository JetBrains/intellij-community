// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;

public class GroovyCompletion30Test extends GroovyCompletionTestBase {
  @Override
  protected void setUp() throws Exception {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testInferArgumentTypeFromClosure() {
    doBasicTest("""
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
  }

  public void testInferArgumentTypeFromLambda() {
    doBasicTest("""
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
  }

  public void testInferArgumentTypeFromClosureInsideClass() {
    doBasicTest("""
                  
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
  }

  public void testInferArgumentTypeForClosure() {
    doBasicTest("""
                  
                  def foo(a, b) { b(a) }
                  
                  foo(1) { a -> a.byteValue() }
                  foo('q') { it.len<caret>}
                  """, """
                  
                  def foo(a, b) { b(a) }
                  
                  foo(1) { a -> a.byteValue() }
                  foo('q') { it.length()<caret>}
                  """);
  }

  public void testInferArgumentTypeFromMethod() {
    doBasicTest("""
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
  }

  public void testCompletionAfterComplexStatement() {
    doBasicTest("""
                  
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
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }
}
