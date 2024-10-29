// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.grape;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.Map;

public class GrabDependenciesTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package groovy.lang; public @interface Grab {}");
    myFixture.addClass("package groovy.lang; public @interface Grapes {}");
    myFixture.addClass("package groovy.lang; public @interface GrabExclude {}");
    myFixture.addClass("package groovy.lang; public @interface GrabResolver {}");
  }

  public void testOneGrab() {
    assertEquals(Map.of("@Grab()", "@Grab()"), queries("@Grab() import xxx"));
  }

  public void testTwoGrabs() {
    assertEquals(Map.of("@Grab('x')", "@Grab('x')", "@Grab('y')", "@Grab('y')"),
                 queries("""
                           @Grab('x') import xxx
                           @Grab('y') import yyy
                           """));
  }

  public void testGrapesResolversExcludes() {
    assertEquals(Map.of("@Grab('x')", "@Grab('x') @GrabExclude('exc') @GrabResolver('res')",
                        "@Grab('y')", "@Grab('y') @GrabExclude('exc') @GrabResolver('res')"),
                 queries("""
                           @Grapes([@Grab('x'),@Grab('y')]) import xxx
                           @GrabResolver('res') @GrabExclude('exc') import yyy
                           """));
  }

  private Map<String, String> queries(String text) {
    return GrabDependencies.prepareQueries(myFixture.configureByText("a.groovy", text));
  }
}
