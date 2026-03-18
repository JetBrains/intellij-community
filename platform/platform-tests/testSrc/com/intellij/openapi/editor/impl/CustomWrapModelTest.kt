// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrapModel

class CustomWrapModelTest : AbstractEditorTest() {

  private val customWrapModel: CustomWrapModel
    get() = editor.customWrapModel

  fun testAddWrap() {
    initText("Hello World Test String")
    
    val wrap = customWrapModel.addWrap(5, 4, 0)!!
    
    assertEquals(5, wrap.offset)
    assertEquals(4, wrap.indent)
  }

  fun testGetWrapsReturnsEmptyListInitially() {
    initText("Hello World Test String")
    
    val wraps = customWrapModel.getWraps()
    
    assertTrue(wraps.isEmpty())
  }

  fun testGetWrapsSortedByOffset() {
    initText("Hello World Test String For Sorting")
    
    // Add wraps in non-sequential order
    customWrapModel.addWrap(15, 2, 0)
    customWrapModel.addWrap(5, 2, 0)
    customWrapModel.addWrap(25, 2, 0)
    customWrapModel.addWrap(10, 2, 0)
    customWrapModel.addWrap(20, 2, 0)
    
    val wraps = customWrapModel.getWraps()
    
    assertEquals(5, wraps.size)
    
    // Verify wraps are sorted by offset
    val offsets = wraps.map { it.offset }
    assertEquals(listOf(5, 10, 15, 20, 25), offsets)
  }

  fun testRemoveWrap() {
    initText("Hello World Test String")
    
    val wrap = customWrapModel.addWrap(5, 2, 0)!!
    assertEquals(1, customWrapModel.getWraps().size)
    
    customWrapModel.removeWrap(wrap)
    
    assertTrue(customWrapModel.getWraps().isEmpty())
  }

  fun testMultipleWrapsAtDifferentOffsets() {
    initText("Hello World Test String")
    
    val wrap1 = customWrapModel.addWrap(5, 2, 0)
    val wrap2 = customWrapModel.addWrap(11, 4, 0)
    val wrap3 = customWrapModel.addWrap(16, 6, 0)
    
    val wraps = customWrapModel.getWraps()
    
    assertEquals(3, wraps.size)
    assertTrue(wraps.contains(wrap1))
    assertTrue(wraps.contains(wrap2))
    assertTrue(wraps.contains(wrap3))
  }
}
