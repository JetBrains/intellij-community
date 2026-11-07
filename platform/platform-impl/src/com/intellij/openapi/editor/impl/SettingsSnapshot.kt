// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorSettings.CaretEasing
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.actions.CaretStopOptions
import com.intellij.openapi.editor.ex.BidiTextDirection
import com.intellij.openapi.editor.state.StateLayout
import com.intellij.openapi.editor.state.StateSnapshot
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KProperty

/**
 * Immutable values of the settings of one editor: the snapshot of its [EditorSettingsState]. [SettingsImpl.getSnapshot]
 * gives the current snapshot.
 *
 * A read needs no lock, and it computes nothing. The accessors have the names of the
 * [com.intellij.openapi.editor.EditorSettings] getters, without the project parameter.
 *
 * [getTabSize] and [isUseTabCharacter] give the values for the project of the settings. For a caller with another
 * project, [SettingsImpl] answers from the code style, unless [isTabSizeOverridden] or [isUseTabCharacterOverridden]
 * is `true`.
 */
@ApiStatus.Internal
class SettingsSnapshot internal constructor(
  layout: StateLayout,
  values: Array<Any?>,
  overrides: Array<Any?>,
) : StateSnapshot(layout, values, overrides) {
  fun isLineNumbersShown(): Boolean = booleanAt(Index.lineNumbersShown)
  fun isLineNumbersAfterIcons(): Boolean = booleanAt(Index.lineNumbersAfterIcons)
  fun getLineNumerationType(): LineNumerationType = valueAt(Index.lineNumerationType) as LineNumerationType
  fun isLineMarkerAreaShown(): Boolean = booleanAt(Index.lineMarkerAreaShown)
  fun areGutterIconsShown(): Boolean = booleanAt(Index.gutterIconsShown)
  fun isFoldingOutlineShown(): Boolean = booleanAt(Index.foldingOutlineShown)
  fun isFoldingOutlineShownOnlyOnHover(): Boolean = booleanAt(Index.foldingOutlineShownOnlyOnHover)
  fun isFoldingEndingsShown(): Boolean = booleanAt(Index.foldingEndingsShown)
  fun isAutoCodeFoldingEnabled(): Boolean = booleanAt(Index.autoCodeFoldingEnabled)
  fun isAllowSingleLogicalLineFolding(): Boolean = booleanAt(Index.singleLogicalLineFoldingAllowed)

  fun isRightMarginShown(): Boolean = booleanAt(Index.rightMarginShown)
  fun isHighlightSelectionOccurrences(): Boolean = booleanAt(Index.selectionOccurrencesHighlighted)
  fun isWhitespacesShown(): Boolean = booleanAt(Index.whitespacesShown)
  fun isLeadingWhitespaceShown(): Boolean = booleanAt(Index.leadingWhitespaceShown)
  fun isInnerWhitespaceShown(): Boolean = booleanAt(Index.innerWhitespaceShown)
  fun isTrailingWhitespaceShown(): Boolean = booleanAt(Index.trailingWhitespaceShown)
  fun isSelectionWhitespaceShown(): Boolean = booleanAt(Index.selectionWhitespaceShown)
  fun isIndentGuidesShown(): Boolean = booleanAt(Index.indentGuidesShown)
  fun isCaretRowShown(): Boolean = booleanAt(Index.caretRowShown)
  fun isShowIntentionBulb(): Boolean = booleanAt(Index.intentionBulbShown)
  fun isShowInspectionWidget(): Boolean = booleanAt(Index.inspectionWidgetShown)
  fun isUseEditorFontInInlays(): Boolean = booleanAt(Index.editorFontInInlaysUsed)
  fun getBidiTextDirection(): BidiTextDirection = valueAt(Index.bidiTextDirection) as BidiTextDirection
  fun isRtlEnabled(): Boolean = booleanAt(Index.rtlEnabled)
  fun areStickyLinesShown(): Boolean = booleanAt(Index.stickyLinesShown) && booleanAt(Index.stickyLinesShownForLanguage)
  fun getStickyLinesLimit(): Int = intAt(Index.stickyLinesLimit)
  fun isShowingSpecialChars(): Boolean = booleanAt(Index.specialCharsShown)
  fun getAdditionalLinesCount(): Int = intAt(Index.additionalLinesCount)
  fun getAdditionalColumnsCount(): Int = intAt(Index.additionalColumnsCount)
  fun getCharacterGridWidthMultiplier(): Float? = valueAt(Index.characterGridWidthMultiplier) as Float?

  fun isBlockCursor(): Boolean = booleanAt(Index.blockCursor)
  fun isFullLineHeightCursor(): Boolean = booleanAt(Index.fullLineHeightCursor)
  fun getLineCursorWidth(): Int = intAt(Index.lineCursorWidth)
  fun isSmoothCaretMovement(): Boolean = booleanAt(Index.smoothCaretMovement)
  fun getCaretEasing(): CaretEasing = valueAt(Index.caretEasing) as CaretEasing
  fun isBlinkCaret(): Boolean = booleanAt(Index.caretBlinking)
  fun getCaretBlinkPeriod(): Int = intAt(Index.caretBlinkPeriod)
  fun isSmoothCaretBlinking(): Boolean = booleanAt(Index.smoothCaretBlinking)
  fun isVirtualSpace(): Boolean = booleanAt(Index.virtualSpace)
  fun isCaretInsideTabs(): Boolean = booleanAt(Index.caretInsideTabs)
  fun isSmartHome(): Boolean = booleanAt(Index.smartHome)
  fun isCamelWords(): Boolean = booleanAt(Index.camelWords)
  fun isMouseClickSelectionHonorsCamelWords(): Boolean = booleanAt(Index.mouseClickSelectionHonorsCamelWords)
  fun getCaretStopOptions(): CaretStopOptions = valueAt(Index.caretStopOptions) as CaretStopOptions
  fun addCaretsOnDoubleCtrl(): Boolean = booleanAt(Index.caretsAddedOnDoubleCtrl)

  fun isUseSoftWraps(): Boolean = booleanAt(Index.softWrapsUsed)
  fun isAllSoftWrapsShown(): Boolean = booleanAt(Index.allSoftWrapsShown)
  fun isPaintSoftWraps(): Boolean = booleanAt(Index.softWrapsPainted)
  fun isUseCustomSoftWrapIndent(): Boolean = booleanAt(Index.customSoftWrapIndentUsed)
  fun getCustomSoftWrapIndent(): Int = intAt(Index.customSoftWrapIndent)

  fun getVerticalScrollOffset(): Int = intAt(Index.verticalScrollOffset)
  fun getVerticalScrollJump(): Int = intAt(Index.verticalScrollJump)
  fun getHorizontalScrollOffset(): Int = intAt(Index.horizontalScrollOffset)
  fun getHorizontalScrollJump(): Int = intAt(Index.horizontalScrollJump)
  fun isAnimatedScrolling(): Boolean = booleanAt(Index.animatedScrolling)
  fun isRefrainFromScrolling(): Boolean = booleanAt(Index.refrainFromScrolling)
  fun isAdditionalPageAtBottom(): Boolean = booleanAt(Index.additionalPageAtBottom)

  fun isDndEnabled(): Boolean = booleanAt(Index.dndEnabled)
  fun isWheelFontChangeEnabled(): Boolean = booleanAt(Index.wheelFontChangeEnabled)
  fun isWheelFontChangePersistent(): Boolean = booleanAt(Index.wheelFontChangePersistent)

  fun isVariableInplaceRenameEnabled(): Boolean = booleanAt(Index.variableInplaceRenameEnabled)
  fun isPreselectRename(): Boolean = booleanAt(Index.renamePreselected)
  fun isInsertParenthesesAutomatically(): Boolean = booleanAt(Index.parenthesesInsertedAutomatically)

  fun getRightMargin(): Int = intAt(Index.rightMargin)
  fun getSoftMargins(): List<Int> = intListAt(Index.softMargins)
  fun isWrapWhenTypingReachesRightMargin(): Boolean = booleanAt(Index.wrapWhenTypingReachesRightMargin)
  fun isUseTabCharacter(): Boolean = booleanAt(Index.tabCharacterUsed)
  fun isUseTabCharacterOverridden(): Boolean = isOverriddenAt(Index.tabCharacterUsed)
  fun getTabSize(): Int = intAt(Index.tabSize)
  fun isTabSizeOverridden(): Boolean = isOverriddenAt(Index.tabSize)

  private fun booleanAt(index: Int): Boolean {
    return valueAt(index) as Boolean
  }

  private fun intAt(index: Int): Int {
    return valueAt(index) as Int
  }

  private fun intListAt(index: Int): List<Int> {
    // Safe: the soft margins are the only list setting, and the state stores them as a List<Int>.
    @Suppress("UNCHECKED_CAST")
    return valueAt(index) as List<Int>
  }

  /**
   * The position of each setting in [EditorSettingsState]. The JVM creates this object at the first accessor call. A
   * state has frozen its properties then, so their positions are known.
   */
  private object Index {
    val lineNumbersShown: Int = indexOf(EditorSettingsState::myAreLineNumbersShown)
    val lineNumbersAfterIcons: Int = indexOf(EditorSettingsState::myAreLineNumbersAfterIcons)
    val lineNumerationType: Int = indexOf(EditorSettingsState::myLineNumeration)
    val lineMarkerAreaShown: Int = indexOf(EditorSettingsState::myLineMarkerAreaShown)
    val gutterIconsShown: Int = indexOf(EditorSettingsState::myGutterIconsShown)
    val foldingOutlineShown: Int = indexOf(EditorSettingsState::myIsFoldingOutlineShown)
    val foldingOutlineShownOnlyOnHover: Int = indexOf(EditorSettingsState::foldingOutlineShownOnlyOnHover)
    val foldingEndingsShown: Int = indexOf(EditorSettingsState::foldingEndingsShown)
    val autoCodeFoldingEnabled: Int = indexOf(EditorSettingsState::myAutoCodeFoldingEnabled)
    val singleLogicalLineFoldingAllowed: Int = indexOf(EditorSettingsState::myAllowSingleLogicalLineFolding)
    val rightMarginShown: Int = indexOf(EditorSettingsState::myIsRightMarginShown)
    val selectionOccurrencesHighlighted: Int = indexOf(EditorSettingsState::myIsHighlightSelectionOccurrences)
    val whitespacesShown: Int = indexOf(EditorSettingsState::myIsWhitespacesShown)
    val leadingWhitespaceShown: Int = indexOf(EditorSettingsState::myIsLeadingWhitespacesShown)
    val innerWhitespaceShown: Int = indexOf(EditorSettingsState::myIsInnerWhitespacesShown)
    val trailingWhitespaceShown: Int = indexOf(EditorSettingsState::myIsTrailingWhitespacesShown)
    val selectionWhitespaceShown: Int = indexOf(EditorSettingsState::myIsSelectionWhitespacesShown)
    val indentGuidesShown: Int = indexOf(EditorSettingsState::myIndentGuidesShown)
    val caretRowShown: Int = indexOf(EditorSettingsState::myCaretRowShown)
    val intentionBulbShown: Int = indexOf(EditorSettingsState::myShowIntentionBulb)
    val inspectionWidgetShown: Int = indexOf(EditorSettingsState::showInspectionWidget)
    val editorFontInInlaysUsed: Int = indexOf(EditorSettingsState::useEditorFontInInlays)
    val bidiTextDirection: Int = indexOf(EditorSettingsState::bidiTextDirection)
    val rtlEnabled: Int = indexOf(EditorSettingsState::rtlEnabled)
    val stickyLinesShown: Int = indexOf(EditorSettingsState::myStickyLinesShown)
    val stickyLinesShownForLanguage: Int = indexOf(EditorSettingsState::myStickyLinesShownForLanguage)
    val stickyLinesLimit: Int = indexOf(EditorSettingsState::myStickyLinesLimit)
    val specialCharsShown: Int = indexOf(EditorSettingsState::showingSpecialCharacters)
    val additionalLinesCount: Int = indexOf(EditorSettingsState::myAdditionalLinesCount)
    val additionalColumnsCount: Int = indexOf(EditorSettingsState::myAdditionalColumnsCount)
    val characterGridWidthMultiplier: Int = indexOf(EditorSettingsState::characterGridWidth)
    val blockCursor: Int = indexOf(EditorSettingsState::myIsBlockCursor)
    val fullLineHeightCursor: Int = indexOf(EditorSettingsState::myIsFullLineHeightCursor)
    val lineCursorWidth: Int = indexOf(EditorSettingsState::myLineCursorWidth)
    val smoothCaretMovement: Int = indexOf(EditorSettingsState::myIsSmoothCaretMovement)
    val caretEasing: Int = indexOf(EditorSettingsState::myCaretEasing)
    val caretBlinking: Int = indexOf(EditorSettingsState::myIsCaretBlinking)
    val caretBlinkPeriod: Int = indexOf(EditorSettingsState::myCaretBlinkingPeriod)
    val smoothCaretBlinking: Int = indexOf(EditorSettingsState::myIsSmoothCaretBlinking)
    val virtualSpace: Int = indexOf(EditorSettingsState::myIsVirtualSpace)
    val caretInsideTabs: Int = indexOf(EditorSettingsState::myIsCaretInsideTabs)
    val smartHome: Int = indexOf(EditorSettingsState::myIsSmartHome)
    val camelWords: Int = indexOf(EditorSettingsState::myIsCamelWords)
    val mouseClickSelectionHonorsCamelWords: Int = indexOf(EditorSettingsState::myIsMouseClickSelectionHonorsCamelWords)
    val caretStopOptions: Int = indexOf(EditorSettingsState::caretStopOptions)
    val caretsAddedOnDoubleCtrl: Int = indexOf(EditorSettingsState::addCaretsOnDoubleCtrl)
    val softWrapsUsed: Int = indexOf(EditorSettingsState::myUseSoftWraps)
    val allSoftWrapsShown: Int = indexOf(EditorSettingsState::allSoftWrapsShown)
    val softWrapsPainted: Int = indexOf(EditorSettingsState::myPaintSoftWraps)
    val customSoftWrapIndentUsed: Int = indexOf(EditorSettingsState::myUseCustomSoftWrapIndent)
    val customSoftWrapIndent: Int = indexOf(EditorSettingsState::myCustomSoftWrapIndent)
    val verticalScrollOffset: Int = indexOf(EditorSettingsState::myVerticalScrollOffset)
    val verticalScrollJump: Int = indexOf(EditorSettingsState::myVerticalScrollJump)
    val horizontalScrollOffset: Int = indexOf(EditorSettingsState::myHorizontalScrollOffset)
    val horizontalScrollJump: Int = indexOf(EditorSettingsState::myHorizontalScrollJump)
    val animatedScrolling: Int = indexOf(EditorSettingsState::myIsAnimatedScrolling)
    val refrainFromScrolling: Int = indexOf(EditorSettingsState::myIsRefrainFromScrolling)
    val additionalPageAtBottom: Int = indexOf(EditorSettingsState::myIsAdditionalPageAtBottom)
    val dndEnabled: Int = indexOf(EditorSettingsState::myIsDndEnabled)
    val wheelFontChangeEnabled: Int = indexOf(EditorSettingsState::myIsWheelFontChangeEnabled)
    val wheelFontChangePersistent: Int = indexOf(EditorSettingsState::wheelFontChangePersistent)
    val variableInplaceRenameEnabled: Int = indexOf(EditorSettingsState::myIsRenameVariablesInplace)
    val renamePreselected: Int = indexOf(EditorSettingsState::myRenamePreselect)
    val parenthesesInsertedAutomatically: Int = indexOf(EditorSettingsState::insertParenthesesAutomatically)
    val rightMargin: Int = indexOf(EditorSettingsState::rightMargin)
    val softMargins: Int = indexOf(EditorSettingsState::softMargins)
    val wrapWhenTypingReachesRightMargin: Int = indexOf(EditorSettingsState::myWrapWhenTypingReachesRightMargin)
    val tabCharacterUsed: Int = indexOf(EditorSettingsState::myUseTabCharacter)
    val tabSize: Int = indexOf(EditorSettingsState::tabSize)

    private fun indexOf(property: KProperty<*>): Int {
      return StateLayout.indexOf(EditorSettingsState::class.java, property)
    }
  }
}
