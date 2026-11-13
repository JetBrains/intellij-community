// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class ExpressionTypeTest implements TypingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

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
