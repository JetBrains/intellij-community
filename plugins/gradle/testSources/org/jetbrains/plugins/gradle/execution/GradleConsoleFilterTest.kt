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
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Test

internal class GradleConsoleFilterTest : GradleConsoleFilterTestCase() {

  override fun getFilter(project: Project): Filter = GradleConsoleFilterProvider.GradleConsoleFilter(project)

  @Test
  fun `build file line match`() {
    assertMatch("Build file '", "/a/build.gradle", "' line: 12")
    assertMatch("Build file '", "C:\\project\\build.gradle", "' line: 12")
    assertMatch("A problem occurred evaluating build file '", "/a/build.gradle", "': 12")
  }

  @Test
  fun `settings file line match`() {
    assertMatch("Settings file '", "/a/settings.gradle", "' line: 3")
    assertMatch("settings file '", """c:\a\settings.gradle""", "': 3")
    assertMatch("settings file '", "C:\\project\\settings.gradle", "': 3")
  }

  @Test
  fun `script line match`() {
    assertMatch("Initialization script '", "/a/init.gradle", "' line: 1")
    assertMatch("Initialization script '", "/user/.gradle/init.d/offline.gradle", "' line: 1")
    assertMatch("Script '", "/a/other.gradle", "' line: 7")
  }

  @Test
  fun `garbage line is not matched`() {
    "Build file '/home/ezm/projects/intellij/one.com/commons-webservice-utils/build.gradle' line: ".shouldNotMatch()
    "Garbage".shouldNotMatch()
  }
}