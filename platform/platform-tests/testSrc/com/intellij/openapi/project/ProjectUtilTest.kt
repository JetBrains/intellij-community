// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import java.io.File

class ProjectUtilTest : HeavyPlatformTestCase() {
  fun testDoNotUseNameAsHashPrefixForIpr() {
    val cachePath = appSystemDir.relativize(project.getProjectCachePath("foo")).toString()
    // remove location hash suffix because it is not constant value (depends on machine)
    assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
      .isEqualTo("foo${File.separatorChar}testdonotusenameashashprefixforipr")
  }

  // https://youtrack.jetbrains.com/issue/IDEA-176128
  fun testVerticalBarInTheProjectName() {
    (project as ProjectEx).setProjectName("World of heavens | Client")

    val cachePath = appSystemDir.relativize(project.getProjectCachePath("test", true)).toString()
    // remove location hash suffix because it is not constant value (depends on machine)
    assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
      .isEqualTo("test${File.separatorChar}World of heavens _ Client")
  }
}