// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Supplier
import kotlin.math.max
import kotlin.reflect.KMutableProperty0

private val LOG: Logger = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS: String = "editor.show.special.chars"

/**
 * The [EditorSettings] of one editor, or of no editor.
 *
 * The values are in an [EditorSettingsState]. A getter reads a state property, and a setter overrides it. When a state
 * property changes, [onStatePropertyChanged] refreshes the editor.
 */
@Internal
class SettingsImpl internal constructor(
  private val editor: EditorImpl?,
  kind: EditorKind?,
  project: Project?,
) : EditorSettings {
  private val state: EditorSettingsState
  private var doNotRefreshEditorFlag: Boolean = false

  @get:Deprecated("use {@link EditorKind}")
  val softWrapAppliancePlace: SoftWrapAppliancePlaces = softWrapAppliancePlaceOf(kind)

  constructor() : this(null, null, null)

  init {
    @Suppress("DEPRECATION")
    state = EditorSettingsState(editor, project, softWrapAppliancePlace)
    state.refreshAll()
    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        onStatePropertyChanged(event)
      }
    })
    // The first computation of the inputs starts here, so that a read starts nothing. It comes after the listener above,
    // which refreshes the editor when a result changes a value. Settings without an editor have no file, so their seeds
    // are the computed values already. In unit-test mode, the state computes the values at once instead.
    if (editor != null && BackgroundStateInput.computesInBackground()) {
      state.recalculateInputs()
    }
  }

  /**
   * The state that holds the values. The Remote Development backend sends it to the frontend.
   */
  fun getState(): EditorSettingsState {
    return state
  }

  /**
   * Recalculates the defaults of all state properties, and the inputs of the state: the language, the margins, and the
   * indent values.
   */
  fun reinitSettings() {
    state.refreshAll()
    // Recompute the inputs at once, instead of waiting for the next reader.
    //
    // Nothing reads the tab character until the user indents, and the Remote Development frontend never reads it at
    // all: it takes the value the backend pushed. A backend that waited for a local reader would leave that
    // frontend indenting with the previous code style. The same computation refreshes the indent options of the document.
    state.recalculateInputs()
  }

  /**
   * Turns soft wraps on without an editor refresh.
   */
  fun setUseSoftWrapsQuiet() {
    doNotRefreshEditorFlag = true
    try {
      state.myUseSoftWraps = true
    } finally {
      doNotRefreshEditorFlag = false
    }
  }

  // EditorSettings, in the order of the interface.

  override fun isRightMarginShown(): Boolean = state.myIsRightMarginShown

  override fun setRightMarginShown(value: Boolean) {
    state.myIsRightMarginShown = value
  }

  override fun isHighlightSelectionOccurrences(): Boolean = state.myIsHighlightSelectionOccurrences

  override fun setHighlightSelectionOccurrences(value: Boolean) {
    state.myIsHighlightSelectionOccurrences = value
  }

  override fun isWhitespacesShown(): Boolean = state.myIsWhitespacesShown

  override fun setWhitespacesShown(value: Boolean) {
    state.myIsWhitespacesShown = value
  }

  override fun isLeadingWhitespaceShown(): Boolean = state.myIsLeadingWhitespacesShown

  override fun setLeadingWhitespaceShown(value: Boolean) {
    state.myIsLeadingWhitespacesShown = value
  }

  override fun isInnerWhitespaceShown(): Boolean = state.myIsInnerWhitespacesShown

  override fun setInnerWhitespaceShown(value: Boolean) {
    state.myIsInnerWhitespacesShown = value
  }

  override fun isTrailingWhitespaceShown(): Boolean = state.myIsTrailingWhitespacesShown

  override fun setTrailingWhitespaceShown(value: Boolean) {
    state.myIsTrailingWhitespacesShown = value
  }

  override fun isSelectionWhitespaceShown(): Boolean = state.myIsSelectionWhitespacesShown

  override fun setSelectionWhitespaceShown(value: Boolean) {
    state.myIsSelectionWhitespacesShown = value
  }

  override fun getRightMargin(project: Project?): Int = state.rightMargin

  override fun setRightMargin(rightMargin: Int) {
    state.rightMargin = rightMargin
  }

  override fun getSoftMargins(): List<Int> = state.softMargins

  override fun setSoftMargins(softMargins: List<Int>?) {
    if (softMargins == null) {
      state.clearOverriding(state::softMargins)
    } else {
      state.softMargins = softMargins
    }
  }

  override fun isWrapWhenTypingReachesRightMargin(project: Project?): Boolean = state.myWrapWhenTypingReachesRightMargin

  override fun setWrapWhenTypingReachesRightMargin(value: Boolean) {
    state.myWrapWhenTypingReachesRightMargin = value
  }

  override fun isLineNumbersShown(): Boolean = state.myAreLineNumbersShown

  override fun setLineNumbersShown(value: Boolean) {
    state.myAreLineNumbersShown = value
  }

  override fun isLineNumbersAfterIcons(): Boolean = state.myAreLineNumbersAfterIcons

  override fun setLineNumbersAfterIcons(value: Boolean) {
    state.myAreLineNumbersAfterIcons = value
  }

  override fun getAdditionalLinesCount(): Int = state.myAdditionalLinesCount

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    state.myAdditionalLinesCount = additionalLinesCount
  }

  override fun getAdditionalColumnsCount(): Int = state.myAdditionalColumnsCount

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    state.myAdditionalColumnsCount = additionalColumnsCount
  }

  override fun isLineMarkerAreaShown(): Boolean = state.myLineMarkerAreaShown

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    state.myLineMarkerAreaShown = lineMarkerAreaShown
  }

  override fun areGutterIconsShown(): Boolean = state.myGutterIconsShown

  override fun setGutterIconsShown(value: Boolean) {
    state.myGutterIconsShown = value
  }

  override fun isFoldingOutlineShown(): Boolean = state.myIsFoldingOutlineShown

  override fun setFoldingOutlineShown(value: Boolean) {
    state.myIsFoldingOutlineShown = value
  }

  override fun isAutoCodeFoldingEnabled(): Boolean = state.myAutoCodeFoldingEnabled

  override fun setAutoCodeFoldingEnabled(value: Boolean) {
    state.myAutoCodeFoldingEnabled = value
  }

  override fun isUseTabCharacter(project: Project?): Boolean {
    if (project == null || project == state.project || state.isOverridden(state::myUseTabCharacter)) {
      return state.currentUseTabCharacter()
    }
    // A project-less editor can get a caller with a project, for example in Fleet (IJPL-26876).
    if (state.project != null) {
      LOG.error("called isUseTabCharacter with foreign project")  // investigating such cases
    }
    return foreignIndentOptions(project).USE_TAB_CHARACTER
  }

  override fun setUseTabCharacter(value: Boolean) {
    state.myUseTabCharacter = value
  }

  override fun getTabSize(project: Project?): Int {
    if (project == null || project == state.project || state.isOverridden(state::tabSize)) {
      return state.currentTabSize()
    }
    return max(1, foreignIndentOptions(project).TAB_SIZE)
  }

  override fun setTabSize(tabSize: Int) {
    state.tabSize = tabSize
  }

  override fun isSmartHome(): Boolean = state.myIsSmartHome

  override fun setSmartHome(value: Boolean) {
    state.myIsSmartHome = value
  }

  override fun isVirtualSpace(): Boolean = state.myIsVirtualSpace

  override fun setVirtualSpace(allow: Boolean) {
    state.myIsVirtualSpace = allow
  }

  override fun getVerticalScrollOffset(): Int = state.myVerticalScrollOffset

  override fun setVerticalScrollOffset(value: Int) {
    setOrClearScrollValue(state::myVerticalScrollOffset, value)
  }

  override fun getVerticalScrollJump(): Int = state.myVerticalScrollJump

  override fun setVerticalScrollJump(value: Int) {
    setOrClearScrollValue(state::myVerticalScrollJump, value)
  }

  override fun getHorizontalScrollOffset(): Int = state.myHorizontalScrollOffset

  override fun setHorizontalScrollOffset(value: Int) {
    setOrClearScrollValue(state::myHorizontalScrollOffset, value)
  }

  override fun getHorizontalScrollJump(): Int = state.myHorizontalScrollJump

  override fun setHorizontalScrollJump(value: Int) {
    setOrClearScrollValue(state::myHorizontalScrollJump, value)
  }

  override fun isCaretInsideTabs(): Boolean = state.myIsCaretInsideTabs

  override fun setCaretInsideTabs(allow: Boolean) {
    state.myIsCaretInsideTabs = allow
  }

  override fun isBlinkCaret(): Boolean = state.myIsCaretBlinking

  override fun setBlinkCaret(value: Boolean) {
    state.myIsCaretBlinking = value
  }

  override fun getCaretBlinkPeriod(): Int = state.myCaretBlinkingPeriod

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    state.myCaretBlinkingPeriod = blinkPeriod
  }

  override fun isSmoothCaretBlinking(): Boolean = state.myIsSmoothCaretBlinking

  override fun setSmoothCaretBlinking(value: Boolean) {
    state.myIsSmoothCaretBlinking = value
  }

  override fun isBlockCursor(): Boolean = state.myIsBlockCursor

  override fun setBlockCursor(value: Boolean) {
    state.myIsBlockCursor = value
  }

  override fun isFullLineHeightCursor(): Boolean = state.myIsFullLineHeightCursor

  override fun setFullLineHeightCursor(value: Boolean) {
    state.myIsFullLineHeightCursor = value
  }

  override fun isSmoothCaretMovement(): Boolean = state.myIsSmoothCaretMovement

  override fun setSmoothCaretMovement(useSmoothCaretMovement: Boolean) {
    state.myIsSmoothCaretMovement = useSmoothCaretMovement
  }

  override fun getCaretEasing(): EditorSettings.CaretEasing = state.myCaretEasing

  override fun isCaretRowShown(): Boolean = state.myCaretRowShown

  override fun setCaretRowShown(value: Boolean) {
    state.myCaretRowShown = value
  }

  override fun getLineCursorWidth(): Int = state.myLineCursorWidth

  override fun setLineCursorWidth(width: Int) {
    state.myLineCursorWidth = width
  }

  override fun isAnimatedScrolling(): Boolean = state.myIsAnimatedScrolling

  override fun setAnimatedScrolling(value: Boolean) {
    state.myIsAnimatedScrolling = value
  }

  override fun isCamelWords(): Boolean = state.myIsCamelWords

  override fun setCamelWords(value: Boolean) {
    state.myIsCamelWords = value
  }

  override fun resetCamelWords() {
    state.clearOverriding(state::myIsCamelWords)
  }

  override fun isAdditionalPageAtBottom(): Boolean = state.myIsAdditionalPageAtBottom

  override fun setAdditionalPageAtBottom(value: Boolean) {
    state.myIsAdditionalPageAtBottom = value
  }

  override fun isDndEnabled(): Boolean = state.myIsDndEnabled

  override fun setDndEnabled(value: Boolean) {
    state.myIsDndEnabled = value
  }

  override fun isWheelFontChangeEnabled(): Boolean = state.myIsWheelFontChangeEnabled

  override fun setWheelFontChangeEnabled(value: Boolean) {
    state.myIsWheelFontChangeEnabled = value
  }

  override fun resetWheelFontChangeEnabled() {
    state.clearOverriding(state::myIsWheelFontChangeEnabled)
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean = state.myIsMouseClickSelectionHonorsCamelWords

  override fun setMouseClickSelectionHonorsCamelWords(value: Boolean) {
    state.myIsMouseClickSelectionHonorsCamelWords = value
  }

  override fun isVariableInplaceRenameEnabled(): Boolean = state.myIsRenameVariablesInplace

  override fun setVariableInplaceRenameEnabled(value: Boolean) {
    state.myIsRenameVariablesInplace = value
  }

  override fun isRefrainFromScrolling(): Boolean = state.myIsRefrainFromScrolling

  override fun setRefrainFromScrolling(value: Boolean) {
    state.myIsRefrainFromScrolling = value
  }

  override fun isIndentGuidesShown(): Boolean = state.myIndentGuidesShown

  override fun setIndentGuidesShown(value: Boolean) {
    state.myIndentGuidesShown = value
  }

  override fun isUseSoftWraps(): Boolean = state.myUseSoftWraps

  override fun setUseSoftWraps(use: Boolean) {
    state.myUseSoftWraps = use
  }

  override fun isAllSoftWrapsShown(): Boolean = EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown

  override fun isPaintSoftWraps(): Boolean = state.myPaintSoftWraps

  override fun setPaintSoftWraps(value: Boolean) {
    state.myPaintSoftWraps = value
  }

  override fun isUseCustomSoftWrapIndent(): Boolean = state.myUseCustomSoftWrapIndent

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    state.myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
  }

  override fun getCustomSoftWrapIndent(): Int = state.myCustomSoftWrapIndent

  override fun setCustomSoftWrapIndent(indent: Int) {
    state.myCustomSoftWrapIndent = indent
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean = state.myAllowSingleLogicalLineFolding

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    state.myAllowSingleLogicalLineFolding = allow
  }

  override fun isPreselectRename(): Boolean = state.myRenamePreselect

  override fun setPreselectRename(value: Boolean) {
    state.myRenamePreselect = value
  }

  override fun isShowIntentionBulb(): Boolean = state.myShowIntentionBulb

  override fun setShowIntentionBulb(show: Boolean) {
    state.myShowIntentionBulb = show
  }

  override fun setLanguageSupplier(languageSupplier: Supplier<out Language?>?) {
    if (languageSupplier == null) {
      state.languageSupplier = null
    } else {
      state.languageSupplier = languageSupplier::get
    }
  }

  override fun isShowingSpecialChars(): Boolean = state.showingSpecialCharacters

  override fun setShowingSpecialChars(value: Boolean) {
    state.showingSpecialCharacters = value
  }

  override fun getLineNumerationType(): LineNumerationType = state.myLineNumeration

  override fun setLineNumerationType(value: LineNumerationType) {
    state.myLineNumeration = value
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically
  }

  override fun areStickyLinesShown(): Boolean = state.myStickyLinesShown && state.myStickyLinesShownForLanguage

  override fun getStickyLinesLimit(): Int = state.myStickyLinesLimit

  override fun getCharacterGridWidthMultiplier(): Float? = state.characterGridWidth

  override fun setCharacterGridWidthMultiplier(value: Float?) {
    state.characterGridWidth = value
  }

  /**
   * Refreshes the editor after a change of a state property, unless the property is in [PROPERTIES_WITHOUT_EDITOR_REFRESH].
   */
  private fun onStatePropertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
    val propertyName = event.propertyName
    if (!doNotRefreshEditorFlag && propertyName !in PROPERTIES_WITHOUT_EDITOR_REFRESH) {
      // The state holds the new value already, so the editor needs no reinit of the settings. That reinit would
      // recalculate every default and compute the inputs again.
      editor?.reinitSettings(true, false)
    }
    when (propertyName) {
      EditorSettingsState::myIsBlockCursor.name, EditorSettingsState::myIsSmoothCaretMovement.name -> {
        editor?.updateCaretCursor()
        editor?.contentComponent?.repaint()
      }
      EditorSettingsState::myIsSmoothCaretBlinking.name -> {
        editor?.restartCaretBlinking()
      }
      EditorSettingsState::myStickyLinesShownForLanguage.name -> {
        editor?.stickyLinesForLangChanged(event)
      }
    }
  }

  /**
   * Sets [property] to [value], or restores its default when [value] is -1, as the scroll setters of [EditorSettings]
   * define.
   */
  private fun setOrClearScrollValue(property: KMutableProperty0<Int>, value: Int) {
    if (value == -1) {
      state.clearOverriding(property)
    } else {
      property.set(value)
    }
  }

  /**
   * The indent options for a caller whose project is not the project of these settings. Without a file, the project
   * settings of the caller answer, and they need no lock.
   */
  private fun foreignIndentOptions(project: Project): IndentOptions {
    val file = getVirtualFile()
    if (file == null) {
      return CodeStyle.getSettings(project).indentOptions
    }
    return CodeStyle.getIndentOptions(project, file)
  }

  private fun getVirtualFile(): VirtualFile? {
    return (editor ?: return null).virtualFile
           ?: FileDocumentManager.getInstance().getFile(editor.document)
  }
}

/**
 * The state properties whose change needs no editor refresh. For example, `myUseTabCharacter` does not change the view.
 */
private val PROPERTIES_WITHOUT_EDITOR_REFRESH: Set<String> = setOf(
  EditorSettingsState::myLineCursorWidth.name,
  EditorSettingsState::myAutoCodeFoldingEnabled.name,
  EditorSettingsState::myAllowSingleLogicalLineFolding.name,
  EditorSettingsState::myVerticalScrollJump.name,
  EditorSettingsState::myHorizontalScrollJump.name,
  EditorSettingsState::myIsBlockCursor.name,
  EditorSettingsState::myIsSmoothCaretMovement.name,
  EditorSettingsState::myIsWhitespacesShown.name,
  EditorSettingsState::myIsLeadingWhitespacesShown.name,
  EditorSettingsState::myIsInnerWhitespacesShown.name,
  EditorSettingsState::myIsTrailingWhitespacesShown.name,
  EditorSettingsState::myIsSelectionWhitespacesShown.name,
  EditorSettingsState::myIsAnimatedScrolling.name,
  EditorSettingsState::myIsAdditionalPageAtBottom.name,
  EditorSettingsState::myIsDndEnabled.name,
  EditorSettingsState::myIsWheelFontChangeEnabled.name,
  EditorSettingsState::myIsMouseClickSelectionHonorsCamelWords.name,
  EditorSettingsState::myIsRenameVariablesInplace.name,
  EditorSettingsState::myIsRefrainFromScrolling.name,
  EditorSettingsState::myPaintSoftWraps.name,
  EditorSettingsState::myUseCustomSoftWrapIndent.name,
  EditorSettingsState::myCustomSoftWrapIndent.name,
  EditorSettingsState::myRenamePreselect.name,
  EditorSettingsState::myWrapWhenTypingReachesRightMargin.name,
  EditorSettingsState::myShowIntentionBulb.name,
  EditorSettingsState::myLineNumeration.name,
  EditorSettingsState::myUseTabCharacter.name,
)

private fun softWrapAppliancePlaceOf(kind: EditorKind?): SoftWrapAppliancePlaces {
  return when (kind) {
    EditorKind.CONSOLE -> SoftWrapAppliancePlaces.CONSOLE
    EditorKind.PREVIEW -> SoftWrapAppliancePlaces.PREVIEW
    EditorKind.UNTYPED,
    EditorKind.MAIN_EDITOR,
    EditorKind.DIFF,
    null -> SoftWrapAppliancePlaces.MAIN_EDITOR
  }
}
