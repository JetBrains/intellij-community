// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.useProject
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProjectUtilTest {
  companion object {
    @ClassRule
    @JvmField
    val app = ApplicationRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun doNotUseNameAsHashPrefixForIpr() {
    val project = ProjectManagerEx.getInstanceEx().openProject(fsRule.fs.getPath("/p.ipr"), createTestOpenProjectOptions(runPostStartUpActivities = false)) as ProjectEx
    project.useProject {
      project.setProjectName("do not use me")

      val cachePath = getSystemDir().relativize(project.getProjectCachePath("foo")).toString()
      // remove location hash suffix because it is not constant value (depends on machine)
      assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
        .isEqualTo("foo${File.separatorChar}p")
    }
  }

  // https://youtrack.jetbrains.com/issue/IDEA-176128
  @Test
  fun verticalBarInTheProjectName() {
    val project = ProjectManagerEx.getInstanceEx().openProject(fsRule.fs.getPath("/p"), createTestOpenProjectOptions(runPostStartUpActivities = false)) as ProjectEx
    project.useProject {
      project.setProjectName("World of heavens | Client")

      val cachePath = getSystemDir().relativize(project.getProjectCachePath("test", isForceNameUse = true)).toString()
      // remove location hash suffix because it is not constant value (depends on machine)
      assertThat(cachePath.substring(0, cachePath.lastIndexOf('.')))
        .isEqualTo("test${File.separatorChar}World of heavens _ Client")
    }
  }
}
