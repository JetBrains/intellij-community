/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.util.GradleVersion;
import org.hamcrest.CoreMatchers;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
* @author Vladislav.Soroka
*/
public class VersionMatcherRule extends TestWatcher {

  /**
   * !When adding new versions here change also list in Idea_Tests_BuildToolsTests in Intellij Teamcity configuration
   */
  public static final Object[][] SUPPORTED_GRADLE_VERSIONS = {
    {"3.0"}, /*{"3.1"}, {"3.2"}, {"3.3"}, {"3.4"},*/ {"3.5"},
    {"4.0"}, /*{"4.1"}, {"4.2"}, {"4.3"}, {"4.4"}, {"4.5.1"}, {"4.6"}, {"4.7"}, {"4.8"}, {"4.9"},*/ {"4.10.3"},
    {"5.0"}, /*{"5.1"}, {"5.2"}, {"5.3.1"}, {"5.4.1"}, {"5.5.1"},*/ {"5.6.2"},
    {"6.0"}, /* {"6.0.1"},  {"6.1"}, {"6.2"}, {"6.3"}, {"6.4"}, {"6.8.3"}, */ {"6.9"},
    {"7.0"}, /* {"7.1"}, {"7.2"}, */ {"7.3.3"}
  };
  public static final String BASE_GRADLE_VERSION = String.valueOf(SUPPORTED_GRADLE_VERSIONS[SUPPORTED_GRADLE_VERSIONS.length - 1][0]);

  @Nullable
  private CustomMatcher<String> myMatcher;

  @NotNull
  public Matcher<String> getMatcher() {
    return myMatcher != null ? myMatcher : CoreMatchers.any(String.class);
  }

  @Override
  protected void starting(Description d) {
    final TargetVersions targetVersions = d.getAnnotation(TargetVersions.class);
    if (targetVersions == null) return;

    myMatcher = new CustomMatcher<String>("Gradle version '" + targetVersions.value() + "'") {
      @Override
      public boolean matches(Object item) {
        return item instanceof String && new VersionMatcher(GradleVersion.version(item.toString())).isVersionMatch(targetVersions);
      }
    };
  }
}
