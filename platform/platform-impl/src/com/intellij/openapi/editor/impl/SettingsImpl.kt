// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
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
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.max

private val LOG = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS: String = "editor.show.special.chars"

@Internal
class SettingsImpl internal constructor(private val editor: EditorImpl?, kind: EditorKind?, project: Project?) : EditorSettings {
  private var languageSupplier: (() -> Language?)? = null

  private val state: EditorSettingsState
  private var doNotRefreshEditorFlag = false

  // This group of settings does not have a UI

  @get:Deprecated("use {@link EditorKind}")
  val softWrapAppliancePlace: SoftWrapAppliancePlaces
  private val computableSettings = ArrayList<CacheableBackgroundComputable<*>>()

  private var indentOptionsUpdateJob: Job? = null
  private val tabSize = object : CacheableBackgroundComputable<Int>(
    CodeStyleSettings.getDefaults().indentOptions.TAB_SIZE) {
    override fun computeValue(project: Project?): Int {
      val tabSize = if (project == null) {
        CodeStyle.getDefaultSettings().getTabSize(null)
      }
      else {
        val file = getVirtualFile()
        if (editor != null && editor.isViewer) {
          val fileType = file?.fileType
          CodeStyle.getSettings(project).getIndentOptions(fileType).TAB_SIZE
        }
        else {
          if (file != null) {
            CodeStyle.getIndentOptions(project, file).TAB_SIZE
          }
          else {
            CodeStyle.getSettings(project).getTabSize(null)
          }
        }
      }
      return max(1, tabSize)
    }

    override fun fireValueChanged(newValue: Int) {
      getState().tabSize = newValue
    }
  }

  constructor() : this(null, null, null)

  init {
    if (EditorKind.CONSOLE == kind) {
      softWrapAppliancePlace = SoftWrapAppliancePlaces.CONSOLE
    }
    else if (EditorKind.PREVIEW == kind) {
      softWrapAppliancePlace = SoftWrapAppliancePlaces.PREVIEW
    }
    else {
      softWrapAppliancePlace = SoftWrapAppliancePlaces.MAIN_EDITOR
    }

    @Suppress("DEPRECATION")
    state = EditorSettingsState(editor, project, softWrapAppliancePlace)
    state.refreshAll()

    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        // todo optimize?
        val propertyName = event.propertyName
        if (!doNotRefreshEditorFlag &&
            propertyName != state::myLineCursorWidth.name &&
            propertyName != state::myAutoCodeFoldingEnabled.name &&
            propertyName != state::myAllowSingleLogicalLineFolding.name &&
            propertyName != state::myVerticalScrollJump.name &&
            propertyName != state::myHorizontalScrollJump.name &&
            propertyName != state::myIsBlockCursor.name &&
            propertyName != state::myIsFullLineHeightCursor.name &&
            propertyName != state::myIsWhitespacesShown.name &&
            propertyName != state::myIsLeadingWhitespacesShown.name &&
            propertyName != state::myIsInnerWhitespacesShown.name &&
            propertyName != state::myIsTrailingWhitespacesShown.name &&
            propertyName != state::myIsSelectionWhitespacesShown.name &&
            propertyName != state::myIsAnimatedScrolling.name &&
            propertyName != state::myIsAdditionalPageAtBottom.name &&
            propertyName != state::myIsDndEnabled.name &&
            propertyName != state::myIsWheelFontChangeEnabled.name &&
            propertyName != state::myIsMouseClickSelectionHonorsCamelWords.name &&
            propertyName != state::myIsRenameVariablesInplace.name &&
            propertyName != state::myIsRefrainFromScrolling.name &&
            propertyName != state::myPaintSoftWraps.name &&
            propertyName != state::myUseCustomSoftWrapIndent.name &&
            propertyName != state::myCustomSoftWrapIndent.name &&
            propertyName != state::myRenamePreselect.name &&
            propertyName != state::myWrapWhenTypingReachesRightMargin.name &&
            propertyName != state::myShowIntentionBulb.name &&
            propertyName != state::myLineNumeration.name &&
            propertyName != state::tabSize.name // `tabSize` is managed by logic located in SettingsImpl
        ) {
          fireEditorRefresh()
        }

        if (propertyName == state::myIsBlockCursor.name ||
            propertyName == state::myIsFullLineHeightCursor.name) {
          editor?.updateCaretCursor()
          editor?.contentComponent?.repaint()
        }

        if (propertyName == state::myStickyLinesShownForLanguage.name) {
          editor?.stickyLinesForLangChanged(event)
        }
      }
    })
  }

  override fun isRightMarginShown(): Boolean {
    return state.myIsRightMarginShown
  }

  override fun setRightMarginShown(`val`: Boolean) {
    state.myIsRightMarginShown = `val`
  }

  override fun isHighlightSelectionOccurrences(): Boolean {
    return state.myIsHighlightSelectionOccurrences
  }

  override fun setHighlightSelectionOccurrences(`val`: Boolean) {
    state.myIsHighlightSelectionOccurrences = `val`
  }

  override fun isWhitespacesShown(): Boolean {
    return state.myIsWhitespacesShown
  }

  override fun setWhitespacesShown(`val`: Boolean) {
    state.myIsWhitespacesShown = `val`
  }

  override fun isLeadingWhitespaceShown(): Boolean {
    return state.myIsLeadingWhitespacesShown
  }

  override fun setLeadingWhitespaceShown(`val`: Boolean) {
    state.myIsLeadingWhitespacesShown = `val`
  }

  override fun isInnerWhitespaceShown(): Boolean {
    return state.myIsInnerWhitespacesShown
  }

  override fun setInnerWhitespaceShown(`val`: Boolean) {
    state.myIsInnerWhitespacesShown = `val`
  }

  override fun isTrailingWhitespaceShown(): Boolean {
    return state.myIsTrailingWhitespacesShown
  }

  override fun setTrailingWhitespaceShown(`val`: Boolean) {
    state.myIsTrailingWhitespacesShown = `val`
  }

  override fun isSelectionWhitespaceShown(): Boolean {
    return state.myIsSelectionWhitespacesShown
  }

  override fun setSelectionWhitespaceShown(`val`: Boolean) {
    state.myIsSelectionWhitespacesShown = `val`
  }

  override fun isIndentGuidesShown(): Boolean {
    return state.myIndentGuidesShown
  }

  override fun setIndentGuidesShown(`val`: Boolean) {
    state.myIndentGuidesShown = `val`
  }

  override fun isLineNumbersShown(): Boolean {
    return state.myAreLineNumbersShown
  }

  override fun setLineNumbersShown(`val`: Boolean) {
    state.myAreLineNumbersShown = `val`
  }

  override fun isLineNumbersAfterIcons(): Boolean {
    return state.myAreLineNumbersAfterIcons
  }

  override fun setLineNumbersAfterIcons(`val`: Boolean) {
    state.myAreLineNumbersAfterIcons = `val`
  }

  override fun areGutterIconsShown(): Boolean {
    return state.myGutterIconsShown
  }

  override fun setGutterIconsShown(`val`: Boolean) {
    state.myGutterIconsShown = `val`
  }

  override fun getRightMargin(project: Project?): Int = state.rightMargin

  override fun isWrapWhenTypingReachesRightMargin(project: Project): Boolean {
    return state.myWrapWhenTypingReachesRightMargin
  }

  override fun setWrapWhenTypingReachesRightMargin(`val`: Boolean) {
    state.myWrapWhenTypingReachesRightMargin = `val`
  }

  override fun setRightMargin(rightMargin: Int) {
    state.rightMargin = rightMargin
  }

  override fun getSoftMargins(): List<Int> = state.softMargins

  override fun setSoftMargins(softMargins: List<Int>?) {
    state.softMargins = softMargins ?: emptyList()
  }

  override fun getAdditionalLinesCount(): Int = state.myAdditionalLinesCount

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    state.myAdditionalLinesCount = additionalLinesCount
  }

  override fun getAdditionalColumnsCount(): Int {
    return state.myAdditionalColumnsCount
  }

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    state.myAdditionalColumnsCount = additionalColumnsCount
  }

  override fun isLineMarkerAreaShown(): Boolean {
    return state.myLineMarkerAreaShown
  }

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    state.myLineMarkerAreaShown = lineMarkerAreaShown
  }

  override fun isFoldingOutlineShown(): Boolean {
    return state.myIsFoldingOutlineShown
  }

  override fun setFoldingOutlineShown(`val`: Boolean) {
    state.myIsFoldingOutlineShown = `val`
  }

  override fun isAutoCodeFoldingEnabled(): Boolean {
    return state.myAutoCodeFoldingEnabled
  }

  override fun setAutoCodeFoldingEnabled(`val`: Boolean) {
    state.myAutoCodeFoldingEnabled = `val`
  }

  override fun isUseTabCharacter(project: Project): Boolean {
    if (project != state.project) {
      // getting here when `state.project` is `null` is expected case (e.g. in fleet)
      if (state.project != null) {
        LOG.error("called isUseTabCharacter with foreign project")  // investigating such cases
      }
      return isUseTabCharacterForForeignProject(project)
    }
    return state.myUseTabCharacter
  }
  private fun isUseTabCharacterForForeignProject(project: Project): Boolean {
    val file = getVirtualFile()
    if (file == null) {
      return CodeStyle.getProjectOrDefaultSettings(project).getIndentOptions(null).USE_TAB_CHARACTER
    }
    else {
      return CodeStyle.getIndentOptions(project, file).USE_TAB_CHARACTER
    }
  }

  override fun setUseTabCharacter(`val`: Boolean) {
    state.myUseTabCharacter = `val`
  }

  fun reinitSettings() {
    for (setting in computableSettings) {
      setting.resetCache()
    }
    state.refreshAll()
    reinitDocumentIndentOptions()
  }

  private fun reinitDocumentIndentOptions() {
    if (editor == null || editor.isViewer) {
      return
    }

    val project = editor.project ?: return
    if (project.isDisposed) {
      return
    }

    val document = editor.document
    val file = getVirtualFile() ?: return
    LOG.debug { "reinitDocumentIndentOptions, file ${file.name}" }
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) {
      app.runReadAction {
        computeIndentOptions(project, file).associateWithDocument(document)
      }
    }
    else {
      val job = (project as ComponentManagerEx).getCoroutineScope().launch {
        val result = readAction {
          computeIndentOptions(project, file)
        }
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          result.associateWithDocument(document)
        }
      }
      job.cancelOnDispose(editor.disposable)

      indentOptionsUpdateJob?.cancel()
      indentOptionsUpdateJob = job
    }
  }

  override fun getTabSize(project: Project?): Int {
    return tabSize.getValue(project)
  }

  private fun getVirtualFile(): VirtualFile? {
    return (editor ?: return null).virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document)
  }

  override fun setTabSize(tabSize: Int) {
    this.tabSize.setValue(tabSize)
  }

  override fun isSmartHome(): Boolean {
    return state.myIsSmartHome
  }

  override fun setSmartHome(`val`: Boolean) {
    state.myIsSmartHome = `val`
  }

  override fun getVerticalScrollOffset(): Int {
    return state.myVerticalScrollOffset
  }

  override fun setVerticalScrollOffset(`val`: Int) {
    if (`val` == -1) state.clearOverriding(state::myVerticalScrollOffset)
    else state.myVerticalScrollOffset = `val`
  }

  override fun getVerticalScrollJump(): Int {
    return state.myVerticalScrollJump
  }

  override fun setVerticalScrollJump(`val`: Int) {
    if (`val` == -1) state.clearOverriding(state::myVerticalScrollJump)
    else state.myVerticalScrollJump = `val`
  }

  override fun getHorizontalScrollOffset(): Int {
    return state.myHorizontalScrollOffset
  }

  override fun setHorizontalScrollOffset(`val`: Int) {
    if (`val` == -1) state.clearOverriding(state::myHorizontalScrollOffset)
    else state.myHorizontalScrollOffset = `val`
  }

  override fun getHorizontalScrollJump(): Int {
    return state.myHorizontalScrollJump
  }

  override fun setHorizontalScrollJump(`val`: Int) {
    if (`val` == -1) state.clearOverriding(state::myHorizontalScrollJump)
    else state.myHorizontalScrollJump = `val`
  }

  override fun isVirtualSpace(): Boolean {
    return state.myIsVirtualSpace
  }

  override fun setVirtualSpace(allow: Boolean) {
    state.myIsVirtualSpace = allow
  }

  override fun isAdditionalPageAtBottom(): Boolean {
    return state.myIsAdditionalPageAtBottom
  }

  override fun setAdditionalPageAtBottom(`val`: Boolean) {
    state.myIsAdditionalPageAtBottom = `val`
  }

  override fun isCaretInsideTabs(): Boolean {
    return state.myIsCaretInsideTabs
  }

  override fun setCaretInsideTabs(allow: Boolean) {
    state.myIsCaretInsideTabs = allow
  }

  override fun isBlockCursor(): Boolean {
    return state.myIsBlockCursor
  }

  override fun setBlockCursor(`val`: Boolean) {
    state.myIsBlockCursor = `val`
  }

  override fun isFullLineHeightCursor(): Boolean {
    return state.myIsFullLineHeightCursor
  }

  override fun setFullLineHeightCursor(`val`: Boolean) {
    state.myIsFullLineHeightCursor = `val`
  }

  override fun isCaretRowShown(): Boolean {
    return state.myCaretRowShown
  }

  override fun setCaretRowShown(`val`: Boolean) {
    state.myCaretRowShown = `val`
  }

  override fun getLineCursorWidth(): Int {
    return state.myLineCursorWidth
  }

  override fun setLineCursorWidth(width: Int) {
    //if (width == myLineCursorWidth) return

    state.myLineCursorWidth = width
    //myDispatcher.multicaster.lineCursorWidthChanged(width)
  }

  override fun isAnimatedScrolling(): Boolean {
    return state.myIsAnimatedScrolling
  }

  override fun setAnimatedScrolling(`val`: Boolean) {
    state.myIsAnimatedScrolling = `val`
  }

  override fun isCamelWords(): Boolean {
    return state.myIsCamelWords
  }

  override fun setCamelWords(`val`: Boolean) {
    state.myIsCamelWords = `val`
  }

  override fun resetCamelWords() {
    state.clearOverriding(state::myIsCamelWords)
  }

  override fun isBlinkCaret(): Boolean {
    return state.myIsCaretBlinking
  }

  override fun setBlinkCaret(`val`: Boolean) {
    state.myIsCaretBlinking = `val`
  }

  override fun getCaretBlinkPeriod(): Int {
    return state.myCaretBlinkingPeriod
  }

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    state.myCaretBlinkingPeriod = blinkPeriod
  }

  override fun isDndEnabled(): Boolean {
    return state.myIsDndEnabled
  }

  override fun setDndEnabled(`val`: Boolean) {
    state.myIsDndEnabled = `val`
  }

  override fun isWheelFontChangeEnabled(): Boolean {
    return state.myIsWheelFontChangeEnabled
  }

  override fun setWheelFontChangeEnabled(`val`: Boolean) {
    state.myIsWheelFontChangeEnabled = `val`
  }

  override fun resetWheelFontChangeEnabled() {
    state.clearOverriding(state::myIsWheelFontChangeEnabled)
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean {
    return state.myIsMouseClickSelectionHonorsCamelWords
  }

  override fun setMouseClickSelectionHonorsCamelWords(`val`: Boolean) {
    state.myIsMouseClickSelectionHonorsCamelWords = `val`
  }

  override fun isVariableInplaceRenameEnabled(): Boolean {
    return state.myIsRenameVariablesInplace
  }

  override fun setVariableInplaceRenameEnabled(`val`: Boolean) {
    state.myIsRenameVariablesInplace = `val`
  }

  override fun isRefrainFromScrolling(): Boolean {
    return state.myIsRefrainFromScrolling
  }

  override fun setRefrainFromScrolling(b: Boolean) {
    state.myIsRefrainFromScrolling = b
  }

  override fun isUseSoftWraps(): Boolean {
    return state.myUseSoftWraps
  }

  override fun setUseSoftWraps(use: Boolean) {
    state.myUseSoftWraps = use
  }

  fun setUseSoftWrapsQuiet() {
    doNotRefreshEditorFlag = true
    try {
      state.myUseSoftWraps = true
    }
    finally {
      doNotRefreshEditorFlag = false
    }
  }

  override fun isAllSoftWrapsShown(): Boolean {
    return EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown
  }

  override fun isPaintSoftWraps(): Boolean {
    return state.myPaintSoftWraps
  }

  override fun setPaintSoftWraps(`val`: Boolean) {
    state.myPaintSoftWraps = `val`
  }

  override fun isUseCustomSoftWrapIndent(): Boolean {
    return state.myUseCustomSoftWrapIndent
  }

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    state.myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
  }

  override fun getCustomSoftWrapIndent(): Int {
    return state.myCustomSoftWrapIndent
  }

  override fun setCustomSoftWrapIndent(indent: Int) {
    state.myCustomSoftWrapIndent = indent
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean {
    return state.myAllowSingleLogicalLineFolding
  }

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    state.myAllowSingleLogicalLineFolding = allow
  }

  private fun fireEditorRefresh(reinitSettings: Boolean = true) {
    editor?.reinitSettings(true, reinitSettings)
  }

  override fun isPreselectRename(): Boolean {
    return state.myRenamePreselect
  }

  override fun setPreselectRename(`val`: Boolean) {
    state.myRenamePreselect = `val`
  }

  override fun isShowIntentionBulb(): Boolean {
    return state.myShowIntentionBulb
  }

  override fun setShowIntentionBulb(show: Boolean) {
    state.myShowIntentionBulb = show
  }

  val language: Language?
    get() = languageSupplier?.invoke()

  override fun setLanguageSupplier(languageSupplier: Supplier<out Language?>?) {
    this.languageSupplier = languageSupplier?.let {
      @Suppress("SuspiciousCallableReferenceInLambda", "RedundantSuppression")
      it::get
    }
    state.languageSupplier = languageSupplier?.let {
      @Suppress("SuspiciousCallableReferenceInLambda", "RedundantSuppression")
      it::get
    }
  }

  override fun isShowingSpecialChars(): Boolean {
    return state.showingSpecialCharacters
  }

  override fun setShowingSpecialChars(value: Boolean) {
    state.showingSpecialCharacters = value
  }

  override fun getLineNumerationType(): LineNumerationType {
    return state.myLineNumeration
  }

  override fun setLineNumerationType(value: LineNumerationType) {
    state.myLineNumeration = value
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically
  }

  override fun areStickyLinesShown(): Boolean {
    return state.myStickyLinesShown && state.myStickyLinesShownForLanguage
  }

  override fun getStickyLinesLimit(): Int {
    return state.myStickyLinesLimit
  }

  override fun getCharacterGridWidthMultiplier(): Float? {
    return state.characterGridWidth
  }

  override fun setCharacterGridWidthMultiplier(value: Float?) {
    state.characterGridWidth = value
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun getState(): EditorSettingsState {
    return state
  }

  private abstract inner class CacheableBackgroundComputable<T>(defaultValue: T) {
    private var overwrittenValue: T? = null
    private var cachedValue: T? = null
    private var defaultValue: T
    private val currentReadActionRef = AtomicReference<Job?>()

    init {
      @Suppress("LeakingThis")
      computableSettings.add(this)
      this.defaultValue = defaultValue
    }

    fun setValue(overwrittenValue: T?) {
      val oldGetValueResult: T
      val newGetValueResult: T

      synchronized(VALUE_LOCK) {
        if (this.overwrittenValue == overwrittenValue) {
          return
        }
        oldGetValueResult = this.overwrittenValue ?: cachedValue ?: defaultValue
        this.overwrittenValue = overwrittenValue
        newGetValueResult = this.overwrittenValue ?: cachedValue ?: defaultValue
      }
      fireEditorRefresh()

      if (newGetValueResult != oldGetValueResult) {
        fireValueChanged(newGetValueResult)
      }
    }

    fun getValue(project: Project?): T {
      synchronized(VALUE_LOCK) {
        overwrittenValue?.let {
          return it
        }
        return cachedValue ?: getDefaultAndCompute(project)
      }
    }

    fun resetCache() {
      synchronized(VALUE_LOCK) { cachedValue = null }
    }

    protected abstract fun computeValue(project: Project?): T

    protected abstract fun fireValueChanged(newValue: T)

    private fun getDefaultAndCompute(project: Project?): T {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return runCatching {
          computeValue(project)
        }.getOrLogException(LOG) ?: defaultValue
      }

      if (currentReadActionRef.get() == null) {
        val readJob = ((project ?: ApplicationManager.getApplication()) as ComponentManagerEx).getCoroutineScope()
          .launch(start = CoroutineStart.LAZY) {
            val result = readAction {
              computeValue(project)
            }
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              currentReadActionRef.set(null)
              val oldGetValueResult: T
              val newGetValueResult: T
              synchronized(VALUE_LOCK) {
                oldGetValueResult = overwrittenValue ?: cachedValue ?: defaultValue
                cachedValue = result
                defaultValue = result
                newGetValueResult = overwrittenValue ?: cachedValue ?: defaultValue
              }
              writeIntentReadAction {
                fireEditorRefresh(false)
                if (oldGetValueResult != newGetValueResult) {
                  fireValueChanged(newGetValueResult)
                }
              }
            }
          }

        if (currentReadActionRef.compareAndSet(null, readJob)) {
          if (editor != null) {
            readJob.cancelOnDispose(editor.disposable)
          }
          readJob.start()
        }
      }
      return defaultValue
    }
  }
}

private val VALUE_LOCK = Any()

private fun computeIndentOptions(project: Project, file: VirtualFile): CommonCodeStyleSettings.IndentOptions {
  return CodeStyle
    .getSettings(project, file)
    .getIndentOptionsByFile(project, file, null, true, null)
}
