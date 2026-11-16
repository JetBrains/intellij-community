// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.platform.settings.v2.impl.providers.DIRECTORY_CONFIG_FILE_NAME
import com.intellij.platform.settings.v2.impl.providers.JETBRAINS_CONFIG_NAME
import com.intellij.platform.settings.v2.impl.providers.PROJECT_CONFIG_FILE_NAME
import com.intellij.platform.settings.v2.impl.providers.localityFileName
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.vcsUtil.VcsUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Checks through the VCS entry points that the local and the environmental config files are declared as ignored, and
 * the team files are not (ADR 0174). [VcsIgnoreManager.isPotentiallyIgnoredFile] asks every [IgnoredFileProvider], and
 * the descriptors of [IgnoredFileProvider.getIgnoredFiles] are the input of the VCS ignore file writer.
 */
@TestApplication
class SettingsIgnoredFilesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project get() = projectFixture.get()
  private val basePath get() = project.basePath!!

  @Test
  fun `the project config files of the local and the environmental locality are ignored, the team file is not`() {
    for (locality in LayerLocality.entries) {
      val file = filePath("$basePath/$JETBRAINS_CONFIG_NAME/${localityFileName(PROJECT_CONFIG_FILE_NAME, locality)}")
      assertIgnored(isIgnoredLocality(locality), file)
    }
  }

  @Test
  fun `the directory config files of the local and the environmental locality are ignored at any depth, the team file is not`() {
    for (dir in listOf(basePath, "$basePath/module", "$basePath/module/src/nested")) {
      for (locality in LayerLocality.entries) {
        val file = filePath("$dir/${localityFileName(DIRECTORY_CONFIG_FILE_NAME, locality)}")
        assertIgnored(isIgnoredLocality(locality), file)
      }
    }
  }

  @Test
  fun `a project config file name outside the project config directory is not ignored`() {
    for (locality in LayerLocality.entries) {
      val file = filePath("$basePath/module/$JETBRAINS_CONFIG_NAME/${localityFileName(PROJECT_CONFIG_FILE_NAME, locality)}")
      assertFalse(VcsIgnoreManager.getInstance(project).isPotentiallyIgnoredFile(file), "potentially ignored: $file")
      assertFalse(matchesDeclaredDescriptor(file), "matches a declared descriptor: $file")
    }
  }

  private fun assertIgnored(expected: Boolean, file: FilePath) {
    assertEquals(expected, VcsIgnoreManager.getInstance(project).isPotentiallyIgnoredFile(file), "potentially ignored: $file")
    assertEquals(expected, matchesDeclaredDescriptor(file), "matches a declared descriptor: $file")
  }

  private fun matchesDeclaredDescriptor(file: FilePath): Boolean =
    IgnoredFileProvider.IGNORE_FILE.extensionList.any { provider -> provider.getIgnoredFiles(project).any { it.matchesFile(file) } }

  private fun isIgnoredLocality(locality: LayerLocality): Boolean = when (locality) {
    LayerLocality.TEAM -> false
    LayerLocality.LOCAL, LayerLocality.ENVIRONMENTAL -> true
  }

  private fun filePath(path: String): FilePath = VcsUtil.getFilePath(path, false)
}
