// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class GradleUntrustedProjectTest : GradleUntrustedProjectTestCase() {

  @Test
  @Disabled("IDEA-362282")
  fun `test untrusted project opening`() {
    runBlocking {
      val project1 = getSimpleProjectInfo("project1")
      val project2 = getSimpleProjectInfo("project2")
      val project3 = getSimpleProjectInfo("project1/project3")
      val project4 = getSimpleProjectInfo("project4")

      assertTrustedLocations(emptyList())
      initProject(project1)
      openProject("project1")
        .useProjectAsync { project ->
          assertProjectLocator(project, "project1")
          assertTrustedLocations("project1")

          initProject(project2)
          linkProject(project, "project2")
          assertProjectLocator(project, "project1", "project2")
          assertTrustedLocations("project1", "project2")

          initProject(project3)
          linkProject(project, "project1/project3")
          assertProjectLocator(project, "project1", "project2")
          assertTrustedLocations("project1", "project2")

          initProject(project4)
          linkProject(project, "project4")
          assertProjectLocator(project, "project1", "project2", "project4")
          assertTrustedLocations("project1", "project2", "project4")
        }
    }
  }
}