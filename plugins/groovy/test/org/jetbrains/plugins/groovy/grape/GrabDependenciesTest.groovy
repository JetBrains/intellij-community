/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.grape;


import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
public class GrabDependenciesTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() {
    super.setUp()
    myFixture.addClass("package groovy.lang; public @interface Grab {}")
    myFixture.addClass("package groovy.lang; public @interface Grapes {}")
    myFixture.addClass("package groovy.lang; public @interface GrabExclude {}")
    myFixture.addClass("package groovy.lang; public @interface GrabResolver {}")
  }

  public void testOneGrab() {
    assert queries("@Grab() import xxx") ==

           ['@Grab()': '@Grab()']
  }

  public void testTwoGrabs() {
    assert queries("""
    @Grab('x') import xxx
    @Grab('y') import yyy """) ==

           ["@Grab('x')": "@Grab('x')", "@Grab('y')": "@Grab('y')"]
  }

  public void testGrapesResolversExcludes() {
    assert queries("""
        @Grapes([@Grab('x'),@Grab('y')]) import xxx
        @GrabResolver('res') @GrabExclude('exc') import yyy
    """) ==
           ["@Grab('x')": "@Grab('x') @GrabExclude('exc') @GrabResolver('res')",
           "@Grab('y')": "@Grab('y') @GrabExclude('exc') @GrabResolver('res')"]
  }

  private Map<String, String> queries(String text) {
    def file = myFixture.configureByText("a.groovy", text)
    return GrabDependencies.prepareQueries(file)
  }
}
