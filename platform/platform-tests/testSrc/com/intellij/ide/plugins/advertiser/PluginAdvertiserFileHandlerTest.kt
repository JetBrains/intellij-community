// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.features.AnsiHighlighterDetector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class PluginAdvertiserFileHandlerTest : BasePlatformTestCase() {
  fun testAnsiHandlerTxt() {
    val file = makeAnsiFile("test.txt")

    val detector = AnsiHighlighterDetector()
    assertTrue("txt file has ANSI codes", detector.isSupported(file))
  }

  fun testAnsiHandlerLog() {
    val file = makeAnsiFile("test.log")

    val detector = AnsiHighlighterDetector()
    assertTrue("log file has ANSI codes", detector.isSupported(file))
  }

  fun testAnsiHandlerNoExtension() {
    val file = makeAnsiFile("test")

    val detector = AnsiHighlighterDetector()
    assertFalse("The file without extension must not be analyzed", detector.isSupported(file))
  }

  private fun makeAnsiFile(name: String): VirtualFile {
    val logBytes = byteArrayOf(27, 91, 51, 49, 109, 72, 101, 108, 108, 111)
    val file = myFixture.createFile(name, "")

    ApplicationManager.getApplication().runWriteAction {
      file.getOutputStream(file).use {
        it.write(logBytes)
      }
    }
    return file
  }
}
