// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class HeadlessRenamePsiElementProcessorTest {

  @Test
  fun `every registration resolves`() {
    val extensions = HeadlessRenamePsiElementProcessor.EP_NAME.extensionList

    assertTrue(extensions.isNotEmpty(), "the platform registers the file processor, so the list is never empty")
  }

  @Test
  fun `a delegating extension is a rename processor`() {
    val delegating = HeadlessRenamePsiElementProcessor.EP_NAME.extensionList
      .filterIsInstance<DelegatingHeadlessRenamePsiElementProcessor>()

    for (extension in delegating) {
      assertInstanceOf(RenamePsiElementProcessorBase::class.java, extension,
                       "${extension.javaClass.name} delegates to the interactive methods of a processor")
    }
  }
}
