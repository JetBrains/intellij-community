// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public class ExpressionTypeTest extends GroovyLatestTest implements TypingTest {
  @Test
  public void untypedLocalVariableReferenceCs() {
    typingTest("@groovy.transform.CompileStatic def usage() { def a; <caret>a }", JAVA_LANG_OBJECT);
  }

  @Test
  public void untypedParameterReferenceCs() {
    typingTest("@groovy.transform.CompileStatic def usage(a) { <caret>a }", JAVA_LANG_OBJECT);
  }

  @Test
  public void ternary_with_primitive_types() {
    expressionTypeTest("char a = '1'; char b = '1'; c ? a : b", JAVA_LANG_CHARACTER);
  }

  @Test
  public void unaryIncrement() {
    getFixture().addFileToProject("classes.groovy", """
      class A { B next() { new B() } }
      class B {}
      """);
    expressionTypeTest("new A()++", "A");
    expressionTypeTest("++new A()", "B");
  }

  @Test
  public void thisInsideAnonymousDefinition() {
    typingTest("""
                 class Test {
                     void main2() {
                         new A(th<caret>is){}
                     }
                 }
                 
                 class A{}
                 """, "Test");
  }

  @Test
  public void superInsideAnonymousDefinition() {
    typingTest("""
                 class Test extends C {
                     void main2() {
                         new A(su<caret>per.equals("")){}
                     }
                 }
                 class C {}
                 class B {}
                 class A extends B {}
                 """, "C");
  }
}
