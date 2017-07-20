/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.project

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import java.io.File

class ProjectUtilTest : PlatformTestCase() {
  fun testDoNotUseNameAsHashPrefixForIpr() {
    val cachePath = appSystemDir.relativize(project.getProjectCachePath("foo")).toString()
    // remove location hash suffix because it is not constant value (depends on machine)
    assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
      .isEqualTo("foo${File.separatorChar}testdonotusenameashashprefixforipr")
  }

  // https://youtrack.jetbrains.com/issue/IDEA-176128
  fun testVerticalBarInTheProjectName() {
    (project as ProjectImpl).setProjectName("World of heavens | Client")

    val cachePath = appSystemDir.relativize(project.getProjectCachePath("test", true)).toString()
    // remove location hash suffix because it is not constant value (depends on machine)
    assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
      .isEqualTo("test${File.separatorChar}World of heavens _ Client")
  }
}