// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class LatestTypeInferenceTest extends GroovyLatestTest implements TypingTest {

  @Test
  public void testNestedCallWithGStringArgumentPassedToStringParameter() {
    typingTest("""
			String foo(String s) { s }
			foo("${42}").with { <caret>it }
			""",
      GrReferenceExpression.class, JAVA_LANG_STRING);
  }

  @Test
  public void testWithCallWithIndexAccessQualifier() {
    typingTest(
      "def usage(Collection<String> strings) { strings[0].with { <caret>it } }",
      GrReferenceExpression.class,
      JAVA_LANG_STRING
    );
    typingTest(
      "def usage(Collection<String> strings) { <caret>strings[0].with { it } }",
      GrIndexProperty.class,
      JAVA_LANG_STRING
    );
  }

  @Test
  public void testTapCallWithIndexAccessQualifier() {
    typingTest(
      "def usage(Collection<String> strings) { strings[0].tap { <caret>it } }",
      GrReferenceExpression.class,
      JAVA_LANG_STRING
    );
    typingTest(
      "def usage(Collection<String> strings) { <caret>strings[0].tap { it } }",
      GrIndexProperty.class,
      JAVA_LANG_STRING
    );
  }

  @Test
  public void testParameterizedReturnType() {
    typingTest(
      """
			interface Consumer<T> {
			    void consume(T t)
			}

			static <R> R nested(R obj, Consumer<R> objConsumer) {
			    obj
			}

			Integer getSomeClass() {
			    nes<caret>ted(1) { }
			}""",
      JAVA_LANG_INTEGER
    );
  }

  @Test
  public void assertExplicitlyTypedSetVariableWithListInitializer() {
    typingTest(
      """
			Set<String> a = ['a']
			assert a instanceof Set
			<caret>a
			""",
      "java.util.Set<java.lang.String>"
    );
  }
}