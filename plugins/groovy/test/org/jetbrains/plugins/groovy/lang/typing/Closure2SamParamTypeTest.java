// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class Closure2SamParamTypeTest implements TypingTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_LATEST);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void addClasses() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("classes.groovy", """
        interface I { def foo(String s) }
        class C { C(int x, I i, String s) {} }
        """);
    });
  }

  @Test
  public void argument_of_method_call() {
    runInEdtAndWait(() -> typingTest("def bar(I i) {}; bar { <caret>it }", JAVA_LANG_STRING));
  }

  @Test
  public void argument_of_literal_constructor() {
    runInEdtAndWait(() -> typingTest("C c = [42, { <caret>it }, \"hi\"]", JAVA_LANG_STRING));
  }

  @Test
  public void implicit_return_statement() {
    runInEdtAndWait(() -> {
      typingTest("""
                   I bar() {
                     { it -> <caret>it }
                   }""", JAVA_LANG_STRING);
    });
  }

  @Test
  public void explicit_return_statement() {
    runInEdtAndWait(() -> {
      typingTest("""
                   I bar() {
                     return { it -> <caret>it }
                   }""", JAVA_LANG_STRING);
    });
  }
}
