// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.ide.presentation.VirtualFilePresentation
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.ui.DeferredIcon
import org.junit.Test
import kotlin.test.assertNotNull

class VirtualFilePresentationTest : BareTestFixtureTestCase() {
  @Test fun noContentLoadingForFilePresentation() {
    val file = object : LightVirtualFile("file.wtf") {
      override fun getInputStream() = throw AssertionError()
      override fun contentsToByteArray() = throw AssertionError()
    }
    val icon = VirtualFilePresentation.getIcon(file)
    assertNotNull(icon)
    if (icon is DeferredIcon) {
      assertNotNull(icon.evaluate())
    }
  }
}