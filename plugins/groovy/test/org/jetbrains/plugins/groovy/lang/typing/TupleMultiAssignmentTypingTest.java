// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

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

import static com.intellij.psi.CommonClassNames.JAVA_LANG_NUMBER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class TupleMultiAssignmentTypingTest implements TypingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

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
