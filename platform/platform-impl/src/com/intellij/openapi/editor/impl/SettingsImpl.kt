// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.actions.CaretStopOptions
import com.intellij.openapi.editor.ex.BidiTextDirection
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.Condition
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Supplier
import kotlin.math.max
import kotlin.reflect.KMutableProperty0

private val LOG: Logger = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS: String = "editor.show.special.chars"

/**
 * The [EditorSettings] of one editor, or of no editor.
 *
 * The values are in an [EditorSettingsState], and a setter overrides a state property. A getter reads the
 * [SettingsSnapshot] of the state. After each batch of state changes, [onStatePropertiesChanged] refreshes the editor
 * once.
 */
@Internal
class SettingsImpl internal constructor(
  private val editor: EditorImpl?,
  kind: EditorKind?,
  project: Project?,
) : EditorSettings {
  private val state: EditorSettingsState = EditorSettingsState(editor, project, softWrapAppliancePlaceOf(kind))
  private var doNotRefreshEditorFlag: Boolean = false
  private var editorRefreshDepth = 0
  private var editorRefreshPending = false

  constructor() : this(null, null, null)

  init {
    state.refreshAll()
    // A read computes nothing, so the first computation of the inputs starts here. Settings without an editor have no
    // file, so their seeds are the computed values already.
    // The computation comes before the listener below. In unit-test mode it runs at once, while the editor is still
    // under construction and must not refresh. In production, the result comes in a later EDT event.
    if (editor != null) {
      state.recalculateInputs()
    }
    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        // The settings react once to all changes of a batch.
      }

      override fun propertiesChanged(events: List<ObservableStateListener.PropertyChangeEvent>) {
        onStatePropertiesChanged(events)
      }
    })
  }

  /**
   * The state that holds the values. The Remote Development backend sends it to the frontend.
   */
  fun getState(): EditorSettingsState {
    return state
  }

  /**
   * The current values. A read needs no lock.
   */
  fun getSnapshot(): SettingsSnapshot {
    return state.getSnapshot()
  }

  /**
   * Recalculates the defaults of all state properties, and the inputs of the state: the language, the margins, and the
   * indent values.
   */
  fun reinitSettings() {
    state.refreshAll()
    // The same computation refreshes the indent options of the document.
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

  /** Applies each state change immediately and refreshes the editor after the outermost action. */
  @RequiresEdt
  fun runWithDeferredEditorRefresh(action: Runnable) {
    editorRefreshDepth++
    try {
      action.run()
    }
    finally {
      editorRefreshDepth--
      if (editorRefreshDepth == 0 && editorRefreshPending) {
        editorRefreshPending = false
        editor?.reinitSettings(true, false)
      }
    }
  }

  // EditorSettings, in the sections of the interface.

  // The gutter.

  override fun isLineNumbersShown(): Boolean {
    return getSnapshot().isLineNumbersShown()
  }

  override fun setLineNumbersShown(value: Boolean) {
    state.myAreLineNumbersShown = value
  }

  override fun isLineNumbersAfterIcons(): Boolean {
    return getSnapshot().isLineNumbersAfterIcons()
  }

  override fun setLineNumbersAfterIcons(value: Boolean) {
    state.myAreLineNumbersAfterIcons = value
  }

  override fun getLineNumerationType(): LineNumerationType {
    return getSnapshot().getLineNumerationType()
  }

  override fun setLineNumerationType(value: LineNumerationType) {
    state.myLineNumeration = value
  }

  override fun isLineMarkerAreaShown(): Boolean {
    return getSnapshot().isLineMarkerAreaShown()
  }

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    state.myLineMarkerAreaShown = lineMarkerAreaShown
  }

  override fun areGutterIconsShown(): Boolean {
    return getSnapshot().areGutterIconsShown()
  }

  override fun setGutterIconsShown(value: Boolean) {
    state.myGutterIconsShown = value
  }

  override fun isFoldingOutlineShown(): Boolean {
    return getSnapshot().isFoldingOutlineShown()
  }

  override fun setFoldingOutlineShown(value: Boolean) {
    state.myIsFoldingOutlineShown = value
  }

  override fun isFoldingOutlineShownOnlyOnHover(): Boolean {
    return getSnapshot().isFoldingOutlineShownOnlyOnHover()
  }

  override fun isFoldingEndingsShown(): Boolean {
    return getSnapshot().isFoldingEndingsShown()
  }

  override fun isAutoCodeFoldingEnabled(): Boolean {
    return getSnapshot().isAutoCodeFoldingEnabled()
  }

  override fun setAutoCodeFoldingEnabled(value: Boolean) {
    state.myAutoCodeFoldingEnabled = value
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean {
    return getSnapshot().isAllowSingleLogicalLineFolding()
  }

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    state.myAllowSingleLogicalLineFolding = allow
  }

  // The view.

  override fun isRightMarginShown(): Boolean {
    return getSnapshot().isRightMarginShown()
  }

  override fun setRightMarginShown(value: Boolean) {
    state.myIsRightMarginShown = value
  }

  override fun isHighlightSelectionOccurrences(): Boolean {
    return getSnapshot().isHighlightSelectionOccurrences()
  }

  override fun setHighlightSelectionOccurrences(value: Boolean) {
    state.myIsHighlightSelectionOccurrences = value
  }

  override fun isWhitespacesShown(): Boolean {
    return getSnapshot().isWhitespacesShown()
  }

  override fun setWhitespacesShown(value: Boolean) {
    state.myIsWhitespacesShown = value
  }

  override fun isLeadingWhitespaceShown(): Boolean {
    return getSnapshot().isLeadingWhitespaceShown()
  }

  override fun setLeadingWhitespaceShown(value: Boolean) {
    state.myIsLeadingWhitespacesShown = value
  }

  override fun isInnerWhitespaceShown(): Boolean {
    return getSnapshot().isInnerWhitespaceShown()
  }

  override fun setInnerWhitespaceShown(value: Boolean) {
    state.myIsInnerWhitespacesShown = value
  }

  override fun isTrailingWhitespaceShown(): Boolean {
    return getSnapshot().isTrailingWhitespaceShown()
  }

  override fun setTrailingWhitespaceShown(value: Boolean) {
    state.myIsTrailingWhitespacesShown = value
  }

  override fun isSelectionWhitespaceShown(): Boolean {
    return getSnapshot().isSelectionWhitespaceShown()
  }

  override fun setSelectionWhitespaceShown(value: Boolean) {
    state.myIsSelectionWhitespacesShown = value
  }

  override fun isIndentGuidesShown(): Boolean {
    return getSnapshot().isIndentGuidesShown()
  }

  override fun setIndentGuidesShown(value: Boolean) {
    state.myIndentGuidesShown = value
  }

  override fun isCaretRowShown(): Boolean {
    return getSnapshot().isCaretRowShown()
  }

  override fun setCaretRowShown(value: Boolean) {
    state.myCaretRowShown = value
  }

  override fun isShowIntentionBulb(): Boolean {
    return getSnapshot().isShowIntentionBulb()
  }

  override fun setShowIntentionBulb(show: Boolean) {
    state.myShowIntentionBulb = show
  }

  override fun isShowInspectionWidget(): Boolean {
    return getSnapshot().isShowInspectionWidget()
  }

  override fun isUseEditorFontInInlays(): Boolean {
    return getSnapshot().isUseEditorFontInInlays()
  }

  override fun getBidiTextDirection(): BidiTextDirection {
    return getSnapshot().getBidiTextDirection()
  }

  override fun isRtlEnabled(): Boolean {
    return getSnapshot().isRtlEnabled()
  }

  override fun setRtlEnabled(value: Boolean) {
    state.rtlEnabled = value
  }

  override fun areStickyLinesShown(): Boolean {
    return getSnapshot().areStickyLinesShown()
  }

  override fun getStickyLinesLimit(): Int {
    return getSnapshot().getStickyLinesLimit()
  }

  override fun isShowingSpecialChars(): Boolean {
    return getSnapshot().isShowingSpecialChars()
  }

  override fun setShowingSpecialChars(value: Boolean) {
    state.showingSpecialCharacters = value
  }

  override fun getAdditionalLinesCount(): Int {
    return getSnapshot().getAdditionalLinesCount()
  }

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    state.myAdditionalLinesCount = additionalLinesCount
  }

  override fun getAdditionalColumnsCount(): Int {
    return getSnapshot().getAdditionalColumnsCount()
  }

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    state.myAdditionalColumnsCount = additionalColumnsCount
  }

  override fun getCharacterGridWidthMultiplier(): Float? {
    return getSnapshot().getCharacterGridWidthMultiplier()
  }

  override fun setCharacterGridWidthMultiplier(value: Float?) {
    state.characterGridWidth = value
  }

  // The caret.

  override fun isBlockCursor(): Boolean {
    return getSnapshot().isBlockCursor()
  }

  override fun setBlockCursor(value: Boolean) {
    state.myIsBlockCursor = value
  }

  override fun isFullLineHeightCursor(): Boolean {
    return getSnapshot().isFullLineHeightCursor()
  }

  override fun setFullLineHeightCursor(value: Boolean) {
    state.myIsFullLineHeightCursor = value
  }

  override fun getLineCursorWidth(): Int {
    return getSnapshot().getLineCursorWidth()
  }

  override fun setLineCursorWidth(width: Int) {
    state.myLineCursorWidth = width
  }

  override fun isSmoothCaretMovement(): Boolean {
    return getSnapshot().isSmoothCaretMovement()
  }

  override fun setSmoothCaretMovement(useSmoothCaretMovement: Boolean) {
    state.myIsSmoothCaretMovement = useSmoothCaretMovement
  }

  override fun getCaretEasing(): EditorSettings.CaretEasing {
    return getSnapshot().getCaretEasing()
  }

  override fun isBlinkCaret(): Boolean {
    return getSnapshot().isBlinkCaret()
  }

  override fun setBlinkCaret(value: Boolean) {
    state.myIsCaretBlinking = value
  }

  override fun getCaretBlinkPeriod(): Int {
    return getSnapshot().getCaretBlinkPeriod()
  }

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    state.myCaretBlinkingPeriod = blinkPeriod
  }

  override fun isSmoothCaretBlinking(): Boolean {
    return getSnapshot().isSmoothCaretBlinking()
  }

  override fun setSmoothCaretBlinking(value: Boolean) {
    state.myIsSmoothCaretBlinking = value
  }

  override fun isVirtualSpace(): Boolean {
    return getSnapshot().isVirtualSpace()
  }

  override fun setVirtualSpace(allow: Boolean) {
    state.myIsVirtualSpace = allow
  }

  override fun isCaretInsideTabs(): Boolean {
    return getSnapshot().isCaretInsideTabs()
  }

  override fun setCaretInsideTabs(allow: Boolean) {
    state.myIsCaretInsideTabs = allow
  }

  override fun isSmartHome(): Boolean {
    return getSnapshot().isSmartHome()
  }

  override fun setSmartHome(value: Boolean) {
    state.myIsSmartHome = value
  }

  override fun isCamelWords(): Boolean {
    return getSnapshot().isCamelWords()
  }

  override fun setCamelWords(value: Boolean) {
    state.myIsCamelWords = value
  }

  override fun resetCamelWords() {
    state.clearOverriding(state::myIsCamelWords)
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean {
    return getSnapshot().isMouseClickSelectionHonorsCamelWords()
  }

  override fun setMouseClickSelectionHonorsCamelWords(value: Boolean) {
    state.myIsMouseClickSelectionHonorsCamelWords = value
  }

  override fun getCaretStopOptions(): CaretStopOptions {
    return getSnapshot().getCaretStopOptions()
  }

  override fun addCaretsOnDoubleCtrl(): Boolean {
    return getSnapshot().addCaretsOnDoubleCtrl()
  }

  // Soft wraps.

  override fun isUseSoftWraps(): Boolean {
    return getSnapshot().isUseSoftWraps()
  }

  override fun setUseSoftWraps(use: Boolean) {
    state.myUseSoftWraps = use
  }

  override fun isAllSoftWrapsShown(): Boolean {
    return getSnapshot().isAllSoftWrapsShown()
  }

  override fun isPaintSoftWraps(): Boolean {
    return getSnapshot().isPaintSoftWraps()
  }

  override fun setPaintSoftWraps(value: Boolean) {
    state.myPaintSoftWraps = value
  }

  override fun isUseCustomSoftWrapIndent(): Boolean {
    return getSnapshot().isUseCustomSoftWrapIndent()
  }

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    state.myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
  }

  override fun getCustomSoftWrapIndent(): Int {
    return getSnapshot().getCustomSoftWrapIndent()
  }

  override fun setCustomSoftWrapIndent(indent: Int) {
    state.myCustomSoftWrapIndent = indent
  }

  // Scrolling.

  override fun getVerticalScrollOffset(): Int {
    return getSnapshot().getVerticalScrollOffset()
  }

  override fun setVerticalScrollOffset(value: Int) {
    setOrClearScrollValue(state::myVerticalScrollOffset, value)
  }

  override fun getVerticalScrollJump(): Int {
    return getSnapshot().getVerticalScrollJump()
  }

  override fun setVerticalScrollJump(value: Int) {
    setOrClearScrollValue(state::myVerticalScrollJump, value)
  }

  override fun getHorizontalScrollOffset(): Int {
    return getSnapshot().getHorizontalScrollOffset()
  }

  override fun setHorizontalScrollOffset(value: Int) {
    setOrClearScrollValue(state::myHorizontalScrollOffset, value)
  }

  override fun getHorizontalScrollJump(): Int {
    return getSnapshot().getHorizontalScrollJump()
  }

  override fun setHorizontalScrollJump(value: Int) {
    setOrClearScrollValue(state::myHorizontalScrollJump, value)
  }

  override fun isAnimatedScrolling(): Boolean {
    return getSnapshot().isAnimatedScrolling()
  }

  override fun setAnimatedScrolling(value: Boolean) {
    state.myIsAnimatedScrolling = value
  }

  override fun isRefrainFromScrolling(): Boolean {
    return getSnapshot().isRefrainFromScrolling()
  }

  override fun setRefrainFromScrolling(value: Boolean) {
    state.myIsRefrainFromScrolling = value
  }

  override fun resetRefrainFromScrolling() {
    state.clearOverriding(state::myIsRefrainFromScrolling)
  }

  override fun isAdditionalPageAtBottom(): Boolean {
    return getSnapshot().isAdditionalPageAtBottom()
  }

  override fun setAdditionalPageAtBottom(value: Boolean) {
    state.myIsAdditionalPageAtBottom = value
  }

  // The mouse.

  override fun isDndEnabled(): Boolean {
    return getSnapshot().isDndEnabled()
  }

  override fun setDndEnabled(value: Boolean) {
    state.myIsDndEnabled = value
  }

  override fun isWheelFontChangeEnabled(): Boolean {
    return getSnapshot().isWheelFontChangeEnabled()
  }

  override fun setWheelFontChangeEnabled(value: Boolean) {
    state.myIsWheelFontChangeEnabled = value
  }

  override fun resetWheelFontChangeEnabled() {
    state.clearOverriding(state::myIsWheelFontChangeEnabled)
  }

  override fun isWheelFontChangePersistent(): Boolean {
    return getSnapshot().isWheelFontChangePersistent()
  }

  // Editing.

  override fun isVariableInplaceRenameEnabled(): Boolean {
    return getSnapshot().isVariableInplaceRenameEnabled()
  }

  override fun setVariableInplaceRenameEnabled(value: Boolean) {
    state.myIsRenameVariablesInplace = value
  }

  override fun isPreselectRename(): Boolean {
    return getSnapshot().isPreselectRename()
  }

  override fun setPreselectRename(value: Boolean) {
    state.myRenamePreselect = value
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return getSnapshot().isInsertParenthesesAutomatically()
  }

  // The code style.

  override fun getRightMargin(project: Project?): Int {
    return getSnapshot().getRightMargin()
  }

  override fun setRightMargin(rightMargin: Int) {
    state.rightMargin = rightMargin
  }

  override fun getSoftMargins(): List<Int> {
    return getSnapshot().getSoftMargins()
  }

  override fun setSoftMargins(softMargins: List<Int>?) {
    if (softMargins == null) {
      state.clearOverriding(state::softMargins)
    } else {
      state.softMargins = softMargins
    }
  }

  override fun isWrapWhenTypingReachesRightMargin(project: Project?): Boolean {
    return getSnapshot().isWrapWhenTypingReachesRightMargin()
  }

  override fun setWrapWhenTypingReachesRightMargin(value: Boolean) {
    state.myWrapWhenTypingReachesRightMargin = value
  }

  override fun isUseTabCharacter(project: Project?): Boolean {
    val current = getSnapshot()
    if (project == null || project == state.project || current.isUseTabCharacterOverridden()) {
      return current.isUseTabCharacter()
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
    val current = getSnapshot()
    if (project == null || project == state.project || current.isTabSizeOverridden()) {
      return current.getTabSize()
    }
    return max(1, foreignIndentOptions(project).TAB_SIZE)
  }

  override fun setTabSize(tabSize: Int) {
    state.tabSize = tabSize
  }

  override fun setLanguageSupplier(languageSupplier: Supplier<out Language?>?) {
    if (languageSupplier == null) {
      state.languageSupplier = null
    } else {
      state.languageSupplier = languageSupplier::get
    }
  }

  /**
   * Refreshes the editor after the changes of one batch. The editor lives on the EDT, so a change on another thread
   * refreshes it later on the EDT, unless the editor is disposed by then.
   */
  private fun onStatePropertiesChanged(events: List<ObservableStateListener.PropertyChangeEvent>) {
    val editor = editor ?: return
    val reinit = !doNotRefreshEditorFlag && events.any { it.propertyName !in PROPERTIES_WITHOUT_EDITOR_REFRESH }
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      refreshEditor(editor, reinit, events)
    } else {
      application.invokeLater(
        { refreshEditor(editor, reinit, events) },
        ModalityState.any(),
        Condition<Any?> { editor.isDisposed },
      )
    }
  }

  /**
   * Reinitializes [editor] once when [reinit] is set, and then runs the reaction of each changed property in [events].
   */
  private fun refreshEditor(editor: EditorImpl, reinit: Boolean, events: List<ObservableStateListener.PropertyChangeEvent>) {
    if (reinit) {
      // The state holds the new values already, so the editor needs no reinit of the settings. That reinit would
      // recalculate every default and compute the inputs again.
      if (editorRefreshDepth > 0) {
        editorRefreshPending = true
      }
      else {
        editor.reinitSettings(true, false)
      }
    }
    for (event in events) {
      when (event.propertyName) {
        EditorSettingsState::myIsBlockCursor.name,
        EditorSettingsState::myIsSmoothCaretMovement.name -> {
          editor.updateCaretCursor()
          editor.contentComponent.repaint()
        }
        EditorSettingsState::myIsSmoothCaretBlinking.name -> {
          editor.restartCaretBlinking()
        }
        EditorSettingsState::myStickyLinesShownForLanguage.name -> {
          editor.stickyLinesForLangChanged(event)
        }
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

  private companion object {
    /**
     * The state properties whose change needs no editor reinit. For example, `myUseTabCharacter` does not change the view.
     * A batch that changes only these properties reinitializes no editor.
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
      EditorSettingsState::wheelFontChangePersistent.name,
      EditorSettingsState::addCaretsOnDoubleCtrl.name,
      EditorSettingsState::insertParenthesesAutomatically.name,
      EditorSettingsState::showInspectionWidget.name,
      EditorSettingsState::caretStopOptions.name,
    )
  }
}

/**
 * The place whose soft wrap setting an editor of [kind] uses. Without a kind, it is the main editor.
 */
internal fun softWrapAppliancePlaceOf(kind: EditorKind?): SoftWrapAppliancePlaces {
  return when (kind) {
    EditorKind.CONSOLE -> SoftWrapAppliancePlaces.CONSOLE
    EditorKind.PREVIEW -> SoftWrapAppliancePlaces.PREVIEW
    EditorKind.UNTYPED,
    EditorKind.MAIN_EDITOR,
    EditorKind.DIFF,
    null -> SoftWrapAppliancePlaces.MAIN_EDITOR
  }
}
