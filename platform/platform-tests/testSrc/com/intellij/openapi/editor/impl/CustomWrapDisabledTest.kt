// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.EmptyCustomWrapModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class CustomWrapDisabledTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testDisabledCustomWrapSupportExposesEmptyModel(): Unit = fixture.runTest {
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(false, testRootDisposable)

    initText("abcd")

    assertFalse(CustomWrapModel.isCustomWrapsSupportEnabled())
    assertSame(EmptyCustomWrapModel, editor.customWrapModel)
    assertNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
    assertFalse(editor.customWrapModel.hasWraps())
    assertTrue(editor.customWrapModel.getWraps().isEmpty())
  }
}
