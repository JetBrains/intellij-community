// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GradleUntrustedProjectTest : GradleUntrustedProjectTestCase() {

  @Test
  fun `test untrusted project opening`() {
    runBlocking {
      initGradleProject("project1")
      initGradleProject("project2")
      initGradleProject("project1/project3")
      initGradleProject("project4")
      assertTrustedLocations(emptyList())
      openProjectAsyncAndWait("project1")
        .useProjectAsync {
          assertProjectLocator(it, "project1")
          assertTrustedLocations("project1")

          linkProjectAsyncAndWait(it, "project2")
          assertProjectLocator(it, "project1", "project2")
          assertTrustedLocations("project1", "project2")

          linkProjectAsyncAndWait(it, "project1/project3")
          assertProjectLocator(it, "project1", "project2")
          assertTrustedLocations("project1", "project2")

          linkProjectAsyncAndWait(it, "project4")
          assertProjectLocator(it, "project1", "project2", "project4")
          assertTrustedLocations("project1", "project2", "project4")
        }
    }
  }
}