// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

import org.jetbrains.plugins.groovy.util.Groovy30Test;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_NUMBER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class TupleMultiAssignmentTypingTest extends Groovy30Test implements TypingTest {
  @Test
  public void type_of_component_is_used_in_multi_declaration() {
    expressionTypeTest("""
                         Tuple2<String, Number> tuple() {}
                         def (s) = tuple()
                         s
                         """, JAVA_LANG_STRING);
  }

  @Test
  public void type_of_component_is_used_in_multi_declaration_2() {
    expressionTypeTest("""
                         Tuple2<String, Number> tuple() {}
                         def (s, n) = tuple()
                         n
                         """, JAVA_LANG_NUMBER);
  }

  @Test
  public void type_of_component_is_used_in_multi_assignment() {
    expressionTypeTest("""
                         Tuple2<String, Number> tuple() {}
                         def s
                         (s) = tuple()
                         s
                         """, JAVA_LANG_STRING);
  }

  @Test
  public void type_of_component_is_used_in_multi_assignment_2() {
    expressionTypeTest("""
                         Tuple2<String, Number> tuple() {}
                         def s,n
                         (s, n) = tuple()
                         n
                         """, JAVA_LANG_NUMBER);
  }
}
