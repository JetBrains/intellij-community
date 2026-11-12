// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class LatestTypeInferenceTest implements TypingTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void testNestedCallWithGStringArgumentPassedToStringParameter() {
    runInEdtAndWait(() -> {
      typingTest("""
  			String foo(String s) { s }
  			foo("${42}").with { <caret>it }
  			""",
        GrReferenceExpression.class, JAVA_LANG_STRING);
    });
  }

  @Test
  public void testWithCallWithIndexAccessQualifier() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testTapCallWithIndexAccessQualifier() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testParameterizedReturnType() {
    runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void assertExplicitlyTypedSetVariableWithListInitializer() {
    runInEdtAndWait(() -> {
      typingTest(
        """
  			Set<String> a = ['a']
  			assert a instanceof Set b
  			<caret>a
  			""",
        "java.util.Set<java.lang.String>"
      );
    });
  }
}