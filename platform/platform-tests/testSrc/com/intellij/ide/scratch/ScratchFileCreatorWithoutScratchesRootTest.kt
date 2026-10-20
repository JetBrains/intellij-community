// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch

import com.intellij.openapi.fileTypes.PlainTextLanguage
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
}
