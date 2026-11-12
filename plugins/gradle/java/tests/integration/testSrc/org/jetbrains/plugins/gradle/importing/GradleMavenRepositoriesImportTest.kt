// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.testFramework.importing.GradleImportingTestCase
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import org.junit.Test

class GradleMavenRepositoriesImportTest: GradleImportingTestCase() {
  @Test
  fun `test maven repositories imported`() {
    currentExternalProjectSettings.isResolveExternalAnnotations = true
    try {
      importProject("""
      |repositories {
      |  maven {
      |    name = "test"
      |    url = "https://example.org/repo"
      |  }
      |}
      |
    """.trimMargin())

      val repositoriesConfiguration = RemoteRepositoriesConfiguration.getInstance(myProject)

      val expectedUrl = "https://example.org/repo"

      assertContainsElements(repositoriesConfiguration.repositories, RemoteRepositoryDescription("test", "test", expectedUrl))
    } finally {
      currentExternalProjectSettings.isResolveExternalAnnotations = false
    }
  }

}