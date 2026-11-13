// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GrUncheckedAssignmentOfRawTypeTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project, "highlighting");

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Override
  public String getTestName() {
    return StringGroovyMethods.capitalize(groovy.get().getTestName(false));
  }

  @Test
  public void rawMethodAccess() { runInEdtAndWait(() -> fileHighlightingTest()); }

  @Test
  public void rawFieldAccess() { runInEdtAndWait(() -> fileHighlightingTest()); }

  @Test
  public void rawArrayStyleAccess() { runInEdtAndWait(() -> fileHighlightingTest()); }

  @Test
  public void rawArrayStyleAccessToMap() { runInEdtAndWait(() -> fileHighlightingTest()); }

  @Test
  public void rawArrayStyleAccessToList() { runInEdtAndWait(() -> fileHighlightingTest()); }

  @Test
  public void rawClosureReturnType() {
    runInEdtAndWait(() -> {
      highlightingTest("""
  class A<T> {
    A(T t) {this.t = t}

    T t
    def cl = {
      return t
    }
  }


  def a = new A(new Date())
  Date d = <warning descr="Cannot assign 'Object' to 'Date'">a.cl()</warning>
  """);
    });
  }

  @Override
  public final @NotNull List<Class<? extends LocalInspectionTool>> getInspections() {
    return Collections.singletonList(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection.class);
  }
}
