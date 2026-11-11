// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class ExpressionTypeTest implements TypingTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void untypedLocalVariableReferenceCs() {
    runInEdtAndWait(() -> typingTest("@groovy.transform.CompileStatic def usage() { def a; <caret>a }", JAVA_LANG_OBJECT));
  }

  @Test
  public void untypedParameterReferenceCs() {
    runInEdtAndWait(() -> typingTest("@groovy.transform.CompileStatic def usage(a) { <caret>a }", JAVA_LANG_OBJECT));
  }

  @Test
  public void ternary_with_primitive_types() {
    runInEdtAndWait(() -> expressionTypeTest("char a = '1'; char b = '1'; c ? a : b", JAVA_LANG_CHARACTER));
  }

  @Test
  public void unaryIncrement() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        class A { B next() { new B() } }
        class B {}
        """);
      expressionTypeTest("new A()++", "A");
      expressionTypeTest("++new A()", "B");
    });
  }

  @Test
  public void thisInsideAnonymousDefinition() {
    runInEdtAndWait(() -> {
      typingTest("""
                   class Test {
                       void main2() {
                           new A(th<caret>is){}
                       }
                   }
                 
                   class A{}
                   """, "Test");
    });
  }

  @Test
  public void superInsideAnonymousDefinition() {
    runInEdtAndWait(() -> {
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
    });
  }
}
