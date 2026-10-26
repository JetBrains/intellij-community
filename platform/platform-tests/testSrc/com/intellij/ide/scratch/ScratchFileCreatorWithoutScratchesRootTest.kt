// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch

import com.intellij.lang.LanguageUtil
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

/**
 * Checks the platform scratch API when the scratch plugin is not loaded.
 * The test masks every root type, so the "Scratches" root is absent.
 */
class ScratchFileCreatorWithoutScratchesRootTest : LightPlatformCodeInsightTestCase() {
  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(RootType.ROOT_EP, emptyList(), testRootDisposable)
  }

  fun testScratchesRootIsAbsent() {
    assertNull(ScratchUtil.findScratchesRootType())
  }

  fun testFileIsNotInScratchesRoot() {
    configureFromFileText("a.txt", "")
    assertFalse(ScratchUtil.isInScratchesRoot(vFile))
    assertFalse(ScratchUtil.isScratch(vFile))
  }

  fun testCreationReturnsNull() {
    val context = ScratchFileCreationHelper.Context()
    context.language = PlainTextLanguage.INSTANCE
    assertNull(ScratchFileCreator.doCreateNewScratch(project, context))
    assertNull(ScratchFileCreator.createScratchFile(project, "scratch.txt", PlainTextLanguage.INSTANCE, "text"))
  }

  fun testFallbackCreatesTempFileWithText() {
    val file = ScratchFileCreator.createScratchOrTempFile(project, "dump.txt", PlainTextLanguage.INSTANCE, "text")!!
    assertEquals("dump.txt", file.name)
    assertEquals("text", VfsUtilCore.loadText(file))
    assertFalse(ScratchUtil.isScratch(file))
    assertFalse(ScratchUtil.isInScratchesRoot(file))
    // NonProjectFileWritingAccessProvider allows a file under the temp directory only outside unit-test mode,
    // so the test checks the location instead of the write access
    assertTrue(FileUtil.isAncestor(FileUtil.getTempDirectory(), file.path, true))
  }

  fun testFallbackAddsLanguageExtension() {
    val file = ScratchFileCreator.createScratchOrTempFile(project, "scheme", XMLLanguage.INSTANCE, "<a/>")!!
    assertEquals("scheme.xml", file.name)
    assertEquals(XMLLanguage.INSTANCE, LanguageUtil.getFileLanguage(file))
  }

  fun testFallbackPicksFreeName() {
    val first = ScratchFileCreator.createScratchOrTempFile(project, "same.txt", null, "1")!!
    val second = ScratchFileCreator.createScratchOrTempFile(project, "same.txt", null, "2")!!
    assertEquals("same.txt", first.name)
    assertEquals("same_1.txt", second.name)
    assertEquals("2", VfsUtilCore.loadText(second))
  }
}
