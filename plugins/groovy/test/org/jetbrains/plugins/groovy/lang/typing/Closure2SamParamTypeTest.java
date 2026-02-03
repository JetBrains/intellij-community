// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.LightProjectTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Before;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class Closure2SamParamTypeTest extends LightProjectTest implements TypingTest {
  @Override
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Before
  public void addClasses() {
    getFixture().addFileToProject("classes.groovy", """
      interface I { def foo(String s) }
      class C { C(int x, I i, String s) {} }
      """);
  }

  @Test
  public void argument_of_method_call() {
    typingTest("def bar(I i) {}; bar { <caret>it }", JAVA_LANG_STRING);
  }

  @Test
  public void argument_of_literal_constructor() {
    typingTest("C c = [42, { <caret>it }, \"hi\"]", JAVA_LANG_STRING);
  }

  @Test
  public void implicit_return_statement() {
    typingTest("""
                 I bar() {
                   { it -> <caret>it }
                 }""", JAVA_LANG_STRING);
  }

  @Test
  public void explicit_return_statement() {
    typingTest("""
                 I bar() {
                   return { it -> <caret>it }
                 }""", JAVA_LANG_STRING);
  }
}
