// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.features.AnsiHighlighterDetector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class PluginAdvertiserFileHandlerTest {
  private val project = projectFixture()
  private val module = project.moduleFixture("src")
  private val sourceRoot = module.sourceRootFixture()

  @Test
  fun testAnsiHandlerTxt(): Unit = timeoutRunBlocking {
    val file = makeAnsiFile("test.txt")
    val detector = AnsiHighlighterDetector()
    assertThat(detector.isSupported(file))
      .withFailMessage { "txt file has ANSI codes" }
      .isTrue
  }

  @Test
  fun testAnsiHandlerLog(): Unit = timeoutRunBlocking {
    val file = makeAnsiFile("test.log")
    val detector = AnsiHighlighterDetector()
    assertThat(detector.isSupported(file))
      .withFailMessage { "log file has ANSI codes" }
      .isTrue
  }

  @Test
  fun testAnsiHandlerNoExtension(): Unit = timeoutRunBlocking {
    val file = makeAnsiFile("test")
    val detector = AnsiHighlighterDetector()
    assertThat(detector.isSupported(file))
      .withFailMessage { "The file without extension must not be analyzed" }
      .isFalse
  }

  private suspend fun makeAnsiFile(name: String): VirtualFile {
    val logBytes = byteArrayOf(27, 91, 51, 49, 109, 72, 101, 108, 108, 111)
    return edtWriteAction {
      val file = sourceRoot.get().createFile(name).virtualFile
      file.getOutputStream(file).use {
        it.write(logBytes)
      }
      file
    }
  }
}
