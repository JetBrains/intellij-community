// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.EmptyCustomWrapModel
import com.intellij.openapi.util.registry.Registry

class CustomWrapDisabledTest : AbstractEditorTest() {

  fun testExperimentalSoftWrapsWithoutCustomWrapRegistryExposeEmptyModel() {
    Registry.get("editor.use.new.soft.wraps.impl").setValue(true, getTestRootDisposable())
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(false, getTestRootDisposable())

    initText("abcd")

    assertFalse(CustomWrapModel.isCustomWrapsSupportEnabled())
    assertSame(EmptyCustomWrapModel, editor.customWrapModel)
    assertNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
    assertFalse(editor.customWrapModel.hasWraps())
    assertTrue(editor.customWrapModel.getWraps().isEmpty())
  }

  fun testLegacySoftWrapImplementationKeepsCustomWrapModelDisabled() {
    Registry.get("editor.use.new.soft.wraps.impl").setValue(false, getTestRootDisposable())
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(true, getTestRootDisposable())

    initText("abcd")

    assertFalse(CustomWrapModel.isCustomWrapsSupportEnabled())
    assertSame(EmptyCustomWrapModel, editor.customWrapModel)
    assertNull(editor.customWrapModel.runBatchMutation { addWrap(2) })
    assertFalse(editor.customWrapModel.hasWraps())
    assertTrue(editor.customWrapModel.getWraps().isEmpty())
  }
}
