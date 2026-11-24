// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists

@TestApplication
class MemoryAnalysisMarkerTest {
  private val marker = Path.of(PathManager.getSystemPath(), "memory-analysis-running")

  @AfterEach
  fun deleteMarker() {
    marker.deleteIfExists()
  }

  @Test
  fun `the marker stays until the last analysis finishes`() {
    LowMemoryNotifier.markMemoryAnalysisRunning().use {
      LowMemoryNotifier.markMemoryAnalysisRunning().use {
        assertThat(marker).exists()
      }
      assertThat(LowMemoryNotifier.isMemoryAnalysisRunning()).isTrue()
      assertThat(marker).exists()
      assertThat(LowMemoryNotifier.deletePreviousMemoryAnalysisMarker()).isFalse()
      assertThat(marker).exists()
    }
    assertThat(marker).doesNotExist()
    assertThat(LowMemoryNotifier.isMemoryAnalysisRunning()).isFalse()
  }

  @Test
  fun `the startup check reports the marker of the previous session once`() {
    marker.createFile()

    assertThat(LowMemoryNotifier.deletePreviousMemoryAnalysisMarker()).isTrue()
    assertThat(marker).doesNotExist()
    assertThat(LowMemoryNotifier.deletePreviousMemoryAnalysisMarker()).isFalse()
  }
}
