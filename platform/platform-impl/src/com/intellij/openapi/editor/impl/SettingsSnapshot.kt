// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorSettings.CaretEasing
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.actions.CaretStopOptions
import com.intellij.openapi.editor.ex.BidiTextDirection
import org.jetbrains.annotations.ApiStatus

/**
 * Immutable values of the settings of one editor. [SettingsImpl.getSnapshot] gives the current snapshot.
 *
 * A read needs no lock, and it computes nothing. The accessors have the names of the
 * [com.intellij.openapi.editor.EditorSettings] getters, without the project parameter.
 *
 * [getTabSize] and [isUseTabCharacter] give the values for the project of the settings. For a caller with another
 * project, [SettingsImpl] answers from the code style, unless [isTabSizeOverridden] or [isUseTabCharacterOverridden]
 * is `true`.
 */
@ApiStatus.Internal
class SettingsSnapshot private constructor(
  private val lineNumbersShown: Boolean,
  private val lineNumbersAfterIcons: Boolean,
  private val lineNumerationType: LineNumerationType,
  private val lineMarkerAreaShown: Boolean,
  private val gutterIconsShown: Boolean,
  private val foldingOutlineShown: Boolean,
  private val foldingOutlineShownOnlyOnHover: Boolean,
  private val foldingEndingsShown: Boolean,
  private val autoCodeFoldingEnabled: Boolean,
  private val singleLogicalLineFoldingAllowed: Boolean,

  private val rightMarginShown: Boolean,
  private val selectionOccurrencesHighlighted: Boolean,
  private val whitespacesShown: Boolean,
  private val leadingWhitespaceShown: Boolean,
  private val innerWhitespaceShown: Boolean,
  private val trailingWhitespaceShown: Boolean,
  private val selectionWhitespaceShown: Boolean,
  private val indentGuidesShown: Boolean,
  private val caretRowShown: Boolean,
  private val intentionBulbShown: Boolean,
  private val inspectionWidgetShown: Boolean,
  private val editorFontInInlaysUsed: Boolean,
  private val bidiTextDirection: BidiTextDirection,
  private val rtlEnabled: Boolean,
  private val stickyLinesShown: Boolean,
  private val stickyLinesShownForLanguage: Boolean,
  private val stickyLinesLimit: Int,
  private val specialCharsShown: Boolean,
  private val additionalLinesCount: Int,
  private val additionalColumnsCount: Int,
  private val characterGridWidthMultiplier: Float?,

  private val blockCursor: Boolean,
  private val fullLineHeightCursor: Boolean,
  private val lineCursorWidth: Int,
  private val smoothCaretMovement: Boolean,
  private val caretEasing: CaretEasing,
  private val caretBlinking: Boolean,
  private val caretBlinkPeriod: Int,
  private val smoothCaretBlinking: Boolean,
  private val virtualSpace: Boolean,
  private val caretInsideTabs: Boolean,
  private val smartHome: Boolean,
  private val camelWords: Boolean,
  private val mouseClickSelectionHonorsCamelWords: Boolean,
  private val caretStopOptions: CaretStopOptions,
  private val caretsAddedOnDoubleCtrl: Boolean,

  private val softWrapsUsed: Boolean,
  private val allSoftWrapsShown: Boolean,
  private val softWrapsPainted: Boolean,
  private val customSoftWrapIndentUsed: Boolean,
  private val customSoftWrapIndent: Int,

  private val verticalScrollOffset: Int,
  private val verticalScrollJump: Int,
  private val horizontalScrollOffset: Int,
  private val horizontalScrollJump: Int,
  private val animatedScrolling: Boolean,
  private val refrainFromScrolling: Boolean,
  private val additionalPageAtBottom: Boolean,

  private val dndEnabled: Boolean,
  private val wheelFontChangeEnabled: Boolean,
  private val wheelFontChangePersistent: Boolean,

  private val variableInplaceRenameEnabled: Boolean,
  private val renamePreselected: Boolean,
  private val parenthesesInsertedAutomatically: Boolean,

  private val rightMargin: Int,
  private val softMargins: List<Int>,
  private val wrapWhenTypingReachesRightMargin: Boolean,
  private val tabCharacterUsed: Boolean,
  private val tabCharacterOverridden: Boolean,
  private val tabSize: Int,
  private val tabSizeOverridden: Boolean,

  private val stateModificationCount: Long,
) {
  fun isLineNumbersShown(): Boolean = lineNumbersShown
  fun isLineNumbersAfterIcons(): Boolean = lineNumbersAfterIcons
  fun getLineNumerationType(): LineNumerationType = lineNumerationType
  fun isLineMarkerAreaShown(): Boolean = lineMarkerAreaShown
  fun areGutterIconsShown(): Boolean = gutterIconsShown
  fun isFoldingOutlineShown(): Boolean = foldingOutlineShown
  fun isFoldingOutlineShownOnlyOnHover(): Boolean = foldingOutlineShownOnlyOnHover
  fun isFoldingEndingsShown(): Boolean = foldingEndingsShown
  fun isAutoCodeFoldingEnabled(): Boolean = autoCodeFoldingEnabled
  fun isAllowSingleLogicalLineFolding(): Boolean = singleLogicalLineFoldingAllowed

  fun isRightMarginShown(): Boolean = rightMarginShown
  fun isHighlightSelectionOccurrences(): Boolean = selectionOccurrencesHighlighted
  fun isWhitespacesShown(): Boolean = whitespacesShown
  fun isLeadingWhitespaceShown(): Boolean = leadingWhitespaceShown
  fun isInnerWhitespaceShown(): Boolean = innerWhitespaceShown
  fun isTrailingWhitespaceShown(): Boolean = trailingWhitespaceShown
  fun isSelectionWhitespaceShown(): Boolean = selectionWhitespaceShown
  fun isIndentGuidesShown(): Boolean = indentGuidesShown
  fun isCaretRowShown(): Boolean = caretRowShown
  fun isShowIntentionBulb(): Boolean = intentionBulbShown
  fun isShowInspectionWidget(): Boolean = inspectionWidgetShown
  fun isUseEditorFontInInlays(): Boolean = editorFontInInlaysUsed
  fun getBidiTextDirection(): BidiTextDirection = bidiTextDirection
  fun isRtlEnabled(): Boolean = rtlEnabled
  fun areStickyLinesShown(): Boolean = stickyLinesShown && stickyLinesShownForLanguage
  fun getStickyLinesLimit(): Int = stickyLinesLimit
  fun isShowingSpecialChars(): Boolean = specialCharsShown
  fun getAdditionalLinesCount(): Int = additionalLinesCount
  fun getAdditionalColumnsCount(): Int = additionalColumnsCount
  fun getCharacterGridWidthMultiplier(): Float? = characterGridWidthMultiplier

  fun isBlockCursor(): Boolean = blockCursor
  fun isFullLineHeightCursor(): Boolean = fullLineHeightCursor
  fun getLineCursorWidth(): Int = lineCursorWidth
  fun isSmoothCaretMovement(): Boolean = smoothCaretMovement
  fun getCaretEasing(): CaretEasing = caretEasing
  fun isBlinkCaret(): Boolean = caretBlinking
  fun getCaretBlinkPeriod(): Int = caretBlinkPeriod
  fun isSmoothCaretBlinking(): Boolean = smoothCaretBlinking
  fun isVirtualSpace(): Boolean = virtualSpace
  fun isCaretInsideTabs(): Boolean = caretInsideTabs
  fun isSmartHome(): Boolean = smartHome
  fun isCamelWords(): Boolean = camelWords
  fun isMouseClickSelectionHonorsCamelWords(): Boolean = mouseClickSelectionHonorsCamelWords
  fun getCaretStopOptions(): CaretStopOptions = caretStopOptions
  fun addCaretsOnDoubleCtrl(): Boolean = caretsAddedOnDoubleCtrl

  fun isUseSoftWraps(): Boolean = softWrapsUsed
  fun isAllSoftWrapsShown(): Boolean = allSoftWrapsShown
  fun isPaintSoftWraps(): Boolean = softWrapsPainted
  fun isUseCustomSoftWrapIndent(): Boolean = customSoftWrapIndentUsed
  fun getCustomSoftWrapIndent(): Int = customSoftWrapIndent

  fun getVerticalScrollOffset(): Int = verticalScrollOffset
  fun getVerticalScrollJump(): Int = verticalScrollJump
  fun getHorizontalScrollOffset(): Int = horizontalScrollOffset
  fun getHorizontalScrollJump(): Int = horizontalScrollJump
  fun isAnimatedScrolling(): Boolean = animatedScrolling
  fun isRefrainFromScrolling(): Boolean = refrainFromScrolling
  fun isAdditionalPageAtBottom(): Boolean = additionalPageAtBottom

  fun isDndEnabled(): Boolean = dndEnabled
  fun isWheelFontChangeEnabled(): Boolean = wheelFontChangeEnabled
  fun isWheelFontChangePersistent(): Boolean = wheelFontChangePersistent

  fun isVariableInplaceRenameEnabled(): Boolean = variableInplaceRenameEnabled
  fun isPreselectRename(): Boolean = renamePreselected
  fun isInsertParenthesesAutomatically(): Boolean = parenthesesInsertedAutomatically

  fun getRightMargin(): Int = rightMargin
  fun getSoftMargins(): List<Int> = softMargins
  fun isWrapWhenTypingReachesRightMargin(): Boolean = wrapWhenTypingReachesRightMargin
  fun isUseTabCharacter(): Boolean = tabCharacterUsed
  fun isUseTabCharacterOverridden(): Boolean = tabCharacterOverridden
  fun getTabSize(): Int = tabSize
  fun isTabSizeOverridden(): Boolean = tabSizeOverridden

  internal fun stateModificationCount(): Long = stateModificationCount

  companion object {
    /**
     * Reads the current values of [state], and the modification count of [state] before them. A change during the read
     * thus leaves the count of the snapshot behind the count of the state, and the next publication reads again.
     */
    internal fun read(state: EditorSettingsState): SettingsSnapshot {
      val modificationCount = state.getModificationCount()
      return SettingsSnapshot(
        lineNumbersShown = state.myAreLineNumbersShown,
        lineNumbersAfterIcons = state.myAreLineNumbersAfterIcons,
        lineNumerationType = state.myLineNumeration,
        lineMarkerAreaShown = state.myLineMarkerAreaShown,
        gutterIconsShown = state.myGutterIconsShown,
        foldingOutlineShown = state.myIsFoldingOutlineShown,
        foldingOutlineShownOnlyOnHover = state.foldingOutlineShownOnlyOnHover,
        foldingEndingsShown = state.foldingEndingsShown,
        autoCodeFoldingEnabled = state.myAutoCodeFoldingEnabled,
        singleLogicalLineFoldingAllowed = state.myAllowSingleLogicalLineFolding,

        rightMarginShown = state.myIsRightMarginShown,
        selectionOccurrencesHighlighted = state.myIsHighlightSelectionOccurrences,
        whitespacesShown = state.myIsWhitespacesShown,
        leadingWhitespaceShown = state.myIsLeadingWhitespacesShown,
        innerWhitespaceShown = state.myIsInnerWhitespacesShown,
        trailingWhitespaceShown = state.myIsTrailingWhitespacesShown,
        selectionWhitespaceShown = state.myIsSelectionWhitespacesShown,
        indentGuidesShown = state.myIndentGuidesShown,
        caretRowShown = state.myCaretRowShown,
        intentionBulbShown = state.myShowIntentionBulb,
        inspectionWidgetShown = state.showInspectionWidget,
        editorFontInInlaysUsed = state.useEditorFontInInlays,
        bidiTextDirection = state.bidiTextDirection,
        rtlEnabled = state.rtlEnabled,
        stickyLinesShown = state.myStickyLinesShown,
        stickyLinesShownForLanguage = state.myStickyLinesShownForLanguage,
        stickyLinesLimit = state.myStickyLinesLimit,
        specialCharsShown = state.showingSpecialCharacters,
        additionalLinesCount = state.myAdditionalLinesCount,
        additionalColumnsCount = state.myAdditionalColumnsCount,
        characterGridWidthMultiplier = state.characterGridWidth,

        blockCursor = state.myIsBlockCursor,
        fullLineHeightCursor = state.myIsFullLineHeightCursor,
        lineCursorWidth = state.myLineCursorWidth,
        smoothCaretMovement = state.myIsSmoothCaretMovement,
        caretEasing = state.myCaretEasing,
        caretBlinking = state.myIsCaretBlinking,
        caretBlinkPeriod = state.myCaretBlinkingPeriod,
        smoothCaretBlinking = state.myIsSmoothCaretBlinking,
        virtualSpace = state.myIsVirtualSpace,
        caretInsideTabs = state.myIsCaretInsideTabs,
        smartHome = state.myIsSmartHome,
        camelWords = state.myIsCamelWords,
        mouseClickSelectionHonorsCamelWords = state.myIsMouseClickSelectionHonorsCamelWords,
        caretStopOptions = state.caretStopOptions,
        caretsAddedOnDoubleCtrl = state.addCaretsOnDoubleCtrl,

        softWrapsUsed = state.myUseSoftWraps,
        allSoftWrapsShown = state.allSoftWrapsShown,
        softWrapsPainted = state.myPaintSoftWraps,
        customSoftWrapIndentUsed = state.myUseCustomSoftWrapIndent,
        customSoftWrapIndent = state.myCustomSoftWrapIndent,

        verticalScrollOffset = state.myVerticalScrollOffset,
        verticalScrollJump = state.myVerticalScrollJump,
        horizontalScrollOffset = state.myHorizontalScrollOffset,
        horizontalScrollJump = state.myHorizontalScrollJump,
        animatedScrolling = state.myIsAnimatedScrolling,
        refrainFromScrolling = state.myIsRefrainFromScrolling,
        additionalPageAtBottom = state.myIsAdditionalPageAtBottom,

        dndEnabled = state.myIsDndEnabled,
        wheelFontChangeEnabled = state.myIsWheelFontChangeEnabled,
        wheelFontChangePersistent = state.wheelFontChangePersistent,

        variableInplaceRenameEnabled = state.myIsRenameVariablesInplace,
        renamePreselected = state.myRenamePreselect,
        parenthesesInsertedAutomatically = state.insertParenthesesAutomatically,

        rightMargin = state.rightMargin,
        softMargins = state.softMargins,
        wrapWhenTypingReachesRightMargin = state.myWrapWhenTypingReachesRightMargin,
        tabCharacterUsed = state.myUseTabCharacter,
        tabCharacterOverridden = state.isOverridden(state::myUseTabCharacter),
        tabSize = state.tabSize,
        tabSizeOverridden = state.isOverridden(state::tabSize),

        stateModificationCount = modificationCount,
      )
    }
  }
}
