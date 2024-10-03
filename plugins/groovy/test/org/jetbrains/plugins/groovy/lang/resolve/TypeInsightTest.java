// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.*;

public class TypeInsightTest extends GroovyLatestTest implements TypingTest {
  @Test
  public void mapIterator() {
    typingTest("def usage(Map<String, Integer> m) { <caret>m.iterator() }", GrMethodCall.class,
               "java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>");
  }

  @Test
  public void mapIteratorExplicit() {
    typingTest("def usage(Map<String, Integer> m) { <caret>org.codehaus.groovy.runtime.DefaultGroovyMethods.iterator(m) }",
               GrMethodCall.class, "java.util.Iterator<java.util.Map.Entry<java.lang.String,java.lang.Integer>>");
  }

  @Test
  public void numberNextPrevious() {
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
  }

  @Test
  public void numberIncDec() {
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
  }

  @Test
  public void intdiv() {
    expressionTypeTest("1.intdiv(2)", JAVA_LANG_INTEGER);
  }
}
