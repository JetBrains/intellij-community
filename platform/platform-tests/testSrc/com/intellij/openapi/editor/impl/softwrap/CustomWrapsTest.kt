// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.SoftWrap
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.util.DocumentEventUtil
import com.intellij.util.DocumentUtil
import java.awt.Graphics
import java.awt.Point

abstract class CustomWrapsTestBase : AbstractEditorTest() {
  protected open val useSoftWraps: Boolean = false

  override fun setUp() {
    super.setUp()
    setUpCustomWrapSupport()
  }

  fun initTextAndSoftWraps(fileText: String, charCountToSoftWrapAt: Int = 5) {
    super.initText(fileText)
    if (useSoftWraps) {
      configureSoftWraps(charCountToSoftWrapAt)
    }
  }

  fun testCustomWrapIsRegisteredInStorage() {
    initTextAndSoftWraps("0123456789")
    val wrapOffset = 5
    addCustomWrap(wrapOffset)
    assertNotNull(editor.softWrapModel.getSoftWrap(wrapOffset))
    (editor as EditorImpl).validateState()
  }

  fun testDeleteInvalidatesWrapsInsideRange() {
    initTextAndSoftWraps("0123456789")
    editor.customWrapModel.runBatchMutation {
      addWrap(1)
      addWrap(4)
      addWrap(8)
    }

    runWriteCommand {
      editor.document.deleteString(2, 6)
    }

    assertEquals(listOf(1, 4), registeredSoftWraps().map { it.start })
    assertStorageConsistent()
  }

  fun testBoundaryMergeKeepsSmallerPriorityWrap() {
    initTextAndSoftWraps("0123456789")
    editor.customWrapModel.runBatchMutation {
      addWrap(2, 5, 10)
      addWrap(4, 1, 1)
    }

    runWriteCommand {
      editor.document.deleteString(2, 4)
    }

    val wrapsAtOffset = registeredSoftWraps().filter { it.start == 2 }
    assertEquals(1, wrapsAtOffset.size)
    assertEquals(1, wrapsAtOffset.single().indentInColumns)
    assertStorageConsistent()
  }

  fun testDeleteAndMerge() {
    initTextAndSoftWraps("0123456789")
    editor.customWrapModel.runBatchMutation {
      addWrap(2, priority = 2)
      addWrap(4)
      addWrap(6, 4)
    }

    runWriteCommand {
      editor.document.deleteString(2, 6)
    }

    val wraps = registeredSoftWraps()
    val customWraps = wraps.filter { it.isCustomSoftWrap }
    assertEquals(listOf(2), customWraps.map { it.start })
    assertEquals(4, customWraps.single().indentInColumns)
    if (useSoftWraps) {
      assertEquals(listOf(2, 3), wraps.map { it.start })
    }
    else {
      assertEquals(listOf(2), wraps.map { it.start })
    }
    assertStorageConsistent()
  }

  fun testCollapsedFoldFiltersWrapsInside() {
    initTextAndSoftWraps("0123456789")
    addCollapsedFoldRegion(2, 7, "...")
    editor.customWrapModel.runBatchMutation {
      addWrap(2)
      addWrap(5)
    }

    val wraps = registeredSoftWraps()
    assertSize(1, wraps)
    assertEquals(2, wraps.single().start)
    assertStorageConsistent()
  }

  fun testMoveWithWrapsInCorridorKeepsStorageSorted() {
    initTextAndSoftWraps("0123456789ABCDEFGHIJ")
    editor.customWrapModel.runBatchMutation {
      addWrap(6, priority = 5)
      addWrap(8)
      addWrap(13, priority = 1)
    }

    runWriteCommand {
      (editor.document as DocumentEx).moveText(5, 9, 12)
    }

    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }

  fun testMixedEditsPreserveEditorState() {
    initTextAndSoftWraps("0123456789ABCDEFGHIJ")
    editor.customWrapModel.runBatchMutation {
      addWrap(2, priority = 3)
      addWrap(6, priority = 1)
      addWrap(15, priority = 2)
    }

    runWriteCommand {
      editor.document.insertString(4, "zz")
      editor.document.deleteString(8, 10)
      (editor.document as DocumentEx).moveText(3, 7, 14)
    }

    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }

  fun testMoveDeletionExpandingFoldRecalculatesWrapsInExpandedRange() {
    initTextAndSoftWraps("abcd\n0123456789ABCDEFGHIJABCDEFGHIJ\nwxyz", charCountToSoftWrapAt = 16)
    // fold second line
    val foldRegion = addCustomFoldRegion(1, 1)
    assertNotNull(foldRegion)
    // wrap in the middle of the folded second line
    addCustomWrap(15, 2)
    assertEmpty(registeredSoftWraps())

    runWriteCommand {
      // (move-)deletion overlapping with custom folding start
      (editor.document as DocumentEx).moveText(2, 8, editor.document.textLength)
    }

    // ... invalidates the custom fold region
    assertFalse(foldRegion!!.isValid)
    // but not the wrap
    assertEquals(listOf(9), editor.customWrapModel.getWraps().map { it.offset })

    // custom wraps previously hidden should become visible (projected as soft wraps) as a consequence
    val wraps = registeredSoftWrapsDesc()
    if (useSoftWraps) {
      // the now expanded long line should become soft-wrapped if enabled
      assertEquals(listOf(9.custom, 23.auto), wraps)
    }
    else {
      assertEquals(listOf(9.custom), wraps)
    }
    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }

  protected fun registeredSoftWrapsDesc(): List<SoftWrapDescriptor> {
    return registeredSoftWraps().map { SoftWrapDescriptor(it.start, it.isCustomSoftWrap) }
  }

  protected fun registeredSoftWraps(): List<SoftWrap> {
    return (editor as EditorEx).softWrapModel.registeredSoftWraps
  }

  protected fun assertStorageConsistent() {
    val wraps = registeredSoftWraps()
    var previous = -1
    for (wrap in wraps) {
      val current = wrap.start
      assertTrue(current > previous)
      val fold = editor.foldingModel.getCollapsedRegionAtOffset(current)
      assertTrue(fold == null || fold.startOffset == current)
      previous = current
    }
  }

  protected fun addCustomWrap(offset: Int, indent: Int = 0): CustomWrap {
    val wrap = editor.customWrapModel.runBatchMutation { addWrap(offset, indent) }
    assertNotNull(wrap)
    return wrap!!
  }

  protected fun installSoftWrapPainterWithWidth(width: Int) {
    (editor.softWrapModel as SoftWrapModelImpl).setSoftWrapPainter(object : SoftWrapPainter {
      override fun paint(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int = width

      override fun getDrawingHorizontalOffset(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int = width

      override fun getMinDrawingWidth(drawingType: SoftWrapDrawingType): Int = width

      override fun canUse(): Boolean = true

      override fun reinit() {}
    })
  }

  fun testCustomWrapIgnoresSoftWrapMarkerWidthInCoordinateMapping() {
    initTextAndSoftWraps("ab")
    val spaceWidth = editor.visualPositionToXY(VisualPosition(0, 1)).x - editor.visualPositionToXY(VisualPosition(0, 0)).x
    installSoftWrapPainterWithWidth(spaceWidth * 3)
    addCustomWrap(1, indent = 2)

    val firstLineY = editor.visualPositionToXY(VisualPosition(0, 0)).y
    val wrappedLineY = editor.visualPositionToXY(VisualPosition(1, 0)).y
    assertEquals(VisualPosition(1, 1), editor.xyToVisualPosition(Point(spaceWidth / 2, wrappedLineY)))

    val endOfWrappedLineX = editor.visualPositionToXY(VisualPosition(0, 1)).x
    val nextVirtualColumnX = editor.visualPositionToXY(VisualPosition(0, 2)).x
    assertEquals(spaceWidth, nextVirtualColumnX - endOfWrappedLineX)
    assertEquals(VisualPosition(0, 2), editor.xyToVisualPosition(Point(endOfWrappedLineX + spaceWidth / 2, firstLineY)))
  }

  fun testCustomWrapIgnoresSoftWrapMarkerWidthBeforeAfterLineEndInlayCoordinateMapping() {
    initTextAndSoftWraps("ab")
    val spaceWidth = editor.visualPositionToXY(VisualPosition(0, 1)).x - editor.visualPositionToXY(VisualPosition(0, 0)).x
    val inlayWidth = spaceWidth * 2
    installSoftWrapPainterWithWidth(spaceWidth * 3)
    addCustomWrap(1)
    addAfterLineEndInlay(2, inlayWidth)

    val wrappedLineY = editor.visualPositionToXY(VisualPosition(1, 0)).y
    val wrappedTextEndX = editor.visualPositionToXY(VisualPosition(1, 1)).x
    val firstInlayX = editor.visualPositionToXY(VisualPosition(1, 2)).x
    assertEquals(spaceWidth, firstInlayX - wrappedTextEndX)
    assertEquals(VisualPosition(1, 2), editor.xyToVisualPosition(Point(firstInlayX + inlayWidth / 4, wrappedLineY)))
    assertEquals(VisualPosition(1, 3), editor.xyToVisualPosition(Point(firstInlayX + inlayWidth * 3 / 4, wrappedLineY)))
  }

  fun testCustomWrapIgnoresSoftWrapMarkerWidthInPreferredSize() {
    initTextAndSoftWraps("ab")
    val widthWithoutWrap = editor.contentComponent.preferredSize.width
    installSoftWrapPainterWithWidth(editor.visualPositionToXY(VisualPosition(0, 1)).x * 3)
    addCustomWrap(1)

    val widthWithWrap = editor.contentComponent.preferredSize.width
    assertTrue(widthWithWrap < widthWithoutWrap)
  }

  fun testCustomWrapInvalidatedOnMoveRightIsRemovedAfterInsertion() {
    initTextAndSoftWraps("01234\n5678", charCountToSoftWrapAt = 10)
    addCustomWrap(2)
    assertSize(1, registeredSoftWraps())

    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (DocumentEventUtil.isMoveInsertion(event)) {
          assertEmpty(registeredSoftWraps())
        }
      }
    }, testRootDisposable)
    runWriteCommand {
      (editor.document as DocumentEx).moveText(2, 3, 6)
    }

    assertEmpty(registeredSoftWraps())
    (editor as EditorImpl).validateState()
  }

  fun testCustomWrapChangesAreDeferredUntilBulkModeFinishes() {
    initTextAndSoftWraps("abcdef", charCountToSoftWrapAt = 100)
    val initialWrap = addCustomWrap(2)

    fun registeredCustomSoftWrapOffsets(): List<Int> {
      return registeredSoftWraps().filter { it.isCustomSoftWrap }.map { it.start }
    }

    fun modelCustomWrapOffsets(): List<Int> {
      return editor.customWrapModel.getWraps().map { it.offset }
    }

    assertEquals(listOf(2), registeredCustomSoftWrapOffsets())
    assertEquals(listOf(2), modelCustomWrapOffsets())

    runWriteCommand {
      DocumentUtil.executeInBulk(editor.document as DocumentEx) {
        editor.customWrapModel.runBatchMutation {
          assertNotNull(addWrap(4))
          assertEquals(listOf(2, 4), modelCustomWrapOffsets())
          assertEquals(listOf(2), registeredCustomSoftWrapOffsets())

          removeWrap(initialWrap)
          assertEquals(listOf(4), modelCustomWrapOffsets())
          assertEquals(listOf(2), registeredCustomSoftWrapOffsets())
        }
      }
    }

    assertEquals(listOf(4), modelCustomWrapOffsets())
    assertEquals(listOf(4), registeredCustomSoftWrapOffsets())
    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }
}

class CustomWrapsOnlyTest : CustomWrapsTestBase() {
  fun testMoveTextToRightReordersCustomWrapStorage() {
    initTextAndSoftWraps("0123456789ABCDEFGHIJ")
    editor.customWrapModel.runBatchMutation {
      addWrap(6, 1)
      addWrap(8, 2)
      addWrap(10, 3)
      addWrap(15, 4)
    }

    runWriteCommand {
      (editor.document as DocumentEx).moveText(5, 9, 12)
    }

    assertEquals(listOf(6 to 3, 9 to 1, 11 to 2, 15 to 4), registeredCustomWraps())
    assertEquals(listOf(6 to 3, 9 to 1, 11 to 2, 15 to 4), modelCustomWraps())
    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }

  fun testMoveTextToLeftReordersCustomWrapStorage() {
    initTextAndSoftWraps("0123456789ABCDEFGHIJ")
    editor.customWrapModel.runBatchMutation {
      addWrap(2, 1)
      addWrap(5, 2)
      addWrap(12, 3)
      addWrap(13, 4)
      addWrap(16, 5)
    }

    runWriteCommand {
      (editor.document as DocumentEx).moveText(10, 14, 3)
    }

    assertEquals(listOf(2 to 1, 5 to 3, 6 to 4, 9 to 2, 16 to 5), registeredCustomWraps())
    assertEquals(listOf(2 to 1, 5 to 3, 6 to 4, 9 to 2, 16 to 5), modelCustomWraps())
    assertStorageConsistent()
    (editor as EditorImpl).validateState()
  }

  private fun registeredCustomWraps(): List<Pair<Int, Int>> {
    return registeredSoftWraps().map { it.start to it.indentInColumns }
  }

  private fun modelCustomWraps(): List<Pair<Int, Int>> {
    return editor.customWrapModel.getWraps().map { it.offset to it.indent }
  }
}

class CustomWrapsWithSoftWrapsEnabledTest : CustomWrapsTestBase() {
  override val useSoftWraps: Boolean = true

  fun testRegularSoftWrapStillUsesMarkerWidthWhenCustomWrapsArePresent() {
    initTextAndSoftWraps("abcdefghijklmnop", 3)
    val spaceWidth = editor.visualPositionToXY(VisualPosition(0, 1)).x - editor.visualPositionToXY(VisualPosition(0, 0)).x
    val markerWidth = spaceWidth * 3
    installSoftWrapPainterWithWidth(markerWidth)
    addCustomWrap(1)

    val regularSoftWrap = registeredSoftWraps().firstOrNull { !it.isCustomSoftWrap }
    assertNotNull(regularSoftWrap)

    val beforeWrapPosition = editor.offsetToVisualPosition(regularSoftWrap!!.start, false, true)
    val beforeWrapX = editor.visualPositionToXY(beforeWrapPosition).x
    val afterMarkerX = editor.visualPositionToXY(VisualPosition(beforeWrapPosition.line, beforeWrapPosition.column + 1)).x
    assertEquals(markerWidth, afterMarkerX - beforeWrapX)
  }

  fun testCorrectIndentsForSoftWrapsInALongLineMixedWithCustomWraps() {
    initTextAndSoftWraps("    012340123401234012340123401234567890123456789", 10)
    editor.customWrapModel.runBatchMutation {
      addWrap(9)
      addWrap(14)
      addWrap(19)
      addWrap(24)
      addWrap(29, 2)
    }

    fun doCheck() {
      val wraps = registeredSoftWraps()
      assertSize(8, wraps)
      assertTrue(wraps.slice(0..<5).all { it.isCustomSoftWrap })
      assertEquals(wraps.slice(5..<8).map { it.indentInColumns }, listOf(5, 5, 5))
      assertEquals(wraps.slice(5..<8).map { it.start }, listOf(37, 42, 47))
    }

    doCheck()
    runWriteCommand { editor.document.insertString(39, "0") }
    doCheck()
  }
}

data class SoftWrapDescriptor(val offset: Int, val isCustom: Boolean)

private val Int.custom get() = SoftWrapDescriptor(this, true)
private val Int.auto get() = SoftWrapDescriptor(this, false)
