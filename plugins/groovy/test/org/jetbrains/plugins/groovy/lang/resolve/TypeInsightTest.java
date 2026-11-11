// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_DOUBLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_LONG;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class TypeInsightTest implements TypingTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void mapIterator() {
    runInEdtAndWait(() -> {
      typingTest("def usage(Map<String, Integer> m) { <caret>m.iterator() }", GrMethodCall.class,
                 "java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>");
    });
  }

  @Test
  public void mapIteratorExplicit() {
    runInEdtAndWait(() -> {
      typingTest("def usage(Map<String, Integer> m) { <caret>org.codehaus.groovy.runtime.DefaultGroovyMethods.iterator(m) }",
                 GrMethodCall.class, "java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>");
    });
  }

  @Test
  public void numberNextPrevious() {
    runInEdtAndWait(() -> {
      expressionTypeTest("1.next()", JAVA_LANG_INTEGER);
      expressionTypeTest("((byte)1).next()", JAVA_LANG_INTEGER);
      expressionTypeTest("((short)1).next()", JAVA_LANG_INTEGER);
      expressionTypeTest("1l.next()", JAVA_LANG_LONG);
      expressionTypeTest("1f.next()", JAVA_LANG_DOUBLE);
      expressionTypeTest("1d.next()", JAVA_LANG_DOUBLE);

      expressionTypeTest("1.previous()", JAVA_LANG_INTEGER);
      expressionTypeTest("((byte)1).previous()", JAVA_LANG_INTEGER);
      expressionTypeTest("((short)1).previous()", JAVA_LANG_INTEGER);
      expressionTypeTest("1l.previous()", JAVA_LANG_LONG);
      expressionTypeTest("1f.previous()", JAVA_LANG_DOUBLE);
      expressionTypeTest("1d.previous()", JAVA_LANG_DOUBLE);
    });
  }

  @Test
  public void numberIncDec() {
    runInEdtAndWait(() -> {
      expressionTypeTest("++1", JAVA_LANG_INTEGER);
      expressionTypeTest("++((byte)1)", JAVA_LANG_INTEGER);
      expressionTypeTest("++((short)1)", JAVA_LANG_INTEGER);
      expressionTypeTest("++1l", JAVA_LANG_LONG);
      expressionTypeTest("++1f", JAVA_LANG_DOUBLE);
      expressionTypeTest("++1d", JAVA_LANG_DOUBLE);

      expressionTypeTest("--1", JAVA_LANG_INTEGER);
      expressionTypeTest("--((byte)1)", JAVA_LANG_INTEGER);
      expressionTypeTest("--((short)1)", JAVA_LANG_INTEGER);
      expressionTypeTest("--1l", JAVA_LANG_LONG);
      expressionTypeTest("--1f", JAVA_LANG_DOUBLE);
      expressionTypeTest("--1d", JAVA_LANG_DOUBLE);
    });
  }

  @Test
  public void intdiv() {
    runInEdtAndWait(() -> expressionTypeTest("1.intdiv(2)", JAVA_LANG_INTEGER));
  }
}
