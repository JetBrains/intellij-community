// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrHighlightingAttributesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testHighlightingAttributes() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.testHighlighting(false, true, false, "highlightingAttributes/test.groovy");
  }

  @Test
  public void testTodoHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.testHighlighting(false, true, false, "highlightingAttributes/todo.groovy");
  }
}
