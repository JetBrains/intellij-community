// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.grape;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrabDependenciesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovy.lang; public @interface Grab {}");
    myFixture.addClass("package groovy.lang; public @interface Grapes {}");
    myFixture.addClass("package groovy.lang; public @interface GrabExclude {}");
    myFixture.addClass("package groovy.lang; public @interface GrabResolver {}");
  }

  @Test
  public void testOneGrab() {
    assertEquals(Map.of("@Grab()", "@Grab()"), queries("@Grab() import xxx"));
  }

  @Test
  public void testTwoGrabs() {
    assertEquals(Map.of("@Grab('x')", "@Grab('x')", "@Grab('y')", "@Grab('y')"),
                 queries("""
                           @Grab('x') import xxx
                           @Grab('y') import yyy
                           """));
  }

  @Test
  public void testGrapesResolversExcludes() {
    assertEquals(Map.of("@Grab('x')", "@Grab('x') @GrabExclude('exc') @GrabResolver('res')",
                        "@Grab('y')", "@Grab('y') @GrabExclude('exc') @GrabResolver('res')"),
                 queries("""
                           @Grapes([@Grab('x'),@Grab('y')]) import xxx
                           @GrabResolver('res') @GrabExclude('exc') import yyy
                           """));
  }

  private Map<String, String> queries(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.groovy", text);
    return ReadAction.computeBlocking(() -> GrabDependencies.prepareQueries(file));
  }
}
