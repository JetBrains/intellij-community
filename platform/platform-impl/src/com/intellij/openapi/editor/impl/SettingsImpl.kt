// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.*
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
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.max

private val LOG = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS: String = "editor.show.special.chars"

class SettingsImpl internal constructor(private val editor: EditorImpl?,
                                        kind: EditorKind?,
                                        project: Project?) : EditorSettings {
  private var languageSupplier: (() -> Language?)? = null

  private val myState: EditorSettingsState
  private var doNotRefreshEditorFlag = false

  // This group of settings does not have a UI

  @get:Deprecated("use {@link EditorKind}")
  val softWrapAppliancePlace: SoftWrapAppliancePlaces
  private val myComputableSettings = ArrayList<CacheableBackgroundComputable<*>>()

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
    myState = EditorSettingsState(editor, project, softWrapAppliancePlace)
    myState.refreshAll()

    myState.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        // todo optimize?
        val propertyName = event.propertyName
        if (!doNotRefreshEditorFlag &&
            propertyName != myState::myLineCursorWidth.name &&
            propertyName != myState::myAutoCodeFoldingEnabled.name &&
            propertyName != myState::myAllowSingleLogicalLineFolding.name &&
            propertyName != myState::myVerticalScrollJump.name &&
            propertyName != myState::myHorizontalScrollJump.name &&
            propertyName != myState::myIsBlockCursor.name &&
            propertyName != myState::myIsFullLineHeightCursor.name &&
            propertyName != myState::myIsWhitespacesShown.name &&
            propertyName != myState::myIsLeadingWhitespacesShown.name &&
            propertyName != myState::myIsInnerWhitespacesShown.name &&
            propertyName != myState::myIsTrailingWhitespacesShown.name &&
            propertyName != myState::myIsSelectionWhitespacesShown.name &&
            propertyName != myState::myIsAnimatedScrolling.name &&
            propertyName != myState::myIsAdditionalPageAtBottom.name &&
            propertyName != myState::myIsDndEnabled.name &&
            propertyName != myState::myIsWheelFontChangeEnabled.name &&
            propertyName != myState::myIsMouseClickSelectionHonorsCamelWords.name &&
            propertyName != myState::myIsRenameVariablesInplace.name &&
            propertyName != myState::myIsRefrainFromScrolling.name &&
            propertyName != myState::myPaintSoftWraps.name &&
            propertyName != myState::myUseCustomSoftWrapIndent.name &&
            propertyName != myState::myCustomSoftWrapIndent.name &&
            propertyName != myState::myRenamePreselect.name &&
            propertyName != myState::myWrapWhenTypingReachesRightMargin.name &&
            propertyName != myState::myShowIntentionBulb.name &&
            propertyName != myState::myLineNumeration.name &&
            propertyName != myState::tabSize.name // `tabSize` is managed by logic located in SettingsImpl
        ) {
          fireEditorRefresh()
        }

        if (propertyName == myState::myIsBlockCursor.name ||
            propertyName == myState::myIsFullLineHeightCursor.name) {
          editor?.updateCaretCursor()
          editor?.contentComponent?.repaint()
        }
      }
    })
  }

  override fun isRightMarginShown(): Boolean {
    return myState.myIsRightMarginShown
  }

  override fun setRightMarginShown(`val`: Boolean) {
    myState.myIsRightMarginShown = `val`
  }

  override fun isWhitespacesShown(): Boolean {
    return myState.myIsWhitespacesShown
  }

  override fun setWhitespacesShown(`val`: Boolean) {
    myState.myIsWhitespacesShown = `val`
  }

  override fun isLeadingWhitespaceShown(): Boolean {
    return myState.myIsLeadingWhitespacesShown
  }

  override fun setLeadingWhitespaceShown(`val`: Boolean) {
    myState.myIsLeadingWhitespacesShown = `val`
  }

  override fun isInnerWhitespaceShown(): Boolean {
    return myState.myIsInnerWhitespacesShown
  }

  override fun setInnerWhitespaceShown(`val`: Boolean) {
    myState.myIsInnerWhitespacesShown = `val`
  }

  override fun isTrailingWhitespaceShown(): Boolean {
    return myState.myIsTrailingWhitespacesShown
  }

  override fun setTrailingWhitespaceShown(`val`: Boolean) {
    myState.myIsTrailingWhitespacesShown = `val`
  }

  override fun isSelectionWhitespaceShown(): Boolean {
    return myState.myIsSelectionWhitespacesShown
  }

  override fun setSelectionWhitespaceShown(`val`: Boolean) {
    myState.myIsSelectionWhitespacesShown = `val`
  }

  override fun isIndentGuidesShown(): Boolean {
    return myState.myIndentGuidesShown
  }

  override fun setIndentGuidesShown(`val`: Boolean) {
    myState.myIndentGuidesShown = `val`
  }

  override fun isLineNumbersShown(): Boolean {
    return myState.myAreLineNumbersShown
  }

  override fun setLineNumbersShown(`val`: Boolean) {
    myState.myAreLineNumbersShown = `val`
  }

  override fun areGutterIconsShown(): Boolean {
    return myState.myGutterIconsShown
  }

  override fun setGutterIconsShown(`val`: Boolean) {
    myState.myGutterIconsShown = `val`
  }

  override fun getRightMargin(project: Project?): Int = myState.rightMargin

  override fun isWrapWhenTypingReachesRightMargin(project: Project): Boolean {
    return myState.myWrapWhenTypingReachesRightMargin
  }

  override fun setWrapWhenTypingReachesRightMargin(`val`: Boolean) {
    myState.myWrapWhenTypingReachesRightMargin = `val`
  }

  override fun setRightMargin(rightMargin: Int) {
    myState.rightMargin = rightMargin
  }

  override fun getSoftMargins(): List<Int> = myState.softMargins

  override fun setSoftMargins(softMargins: List<Int>?) {
    myState.softMargins = softMargins ?: emptyList()
  }

  override fun getAdditionalLinesCount(): Int = myState.myAdditionalLinesCount

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    myState.myAdditionalLinesCount = additionalLinesCount
  }

  override fun getAdditionalColumnsCount(): Int {
    return myState.myAdditionalColumnsCount
  }

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    myState.myAdditionalColumnsCount = additionalColumnsCount
  }

  override fun isLineMarkerAreaShown(): Boolean {
    return myState.myLineMarkerAreaShown
  }

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    myState.myLineMarkerAreaShown = lineMarkerAreaShown
  }

  override fun isFoldingOutlineShown(): Boolean {
    return myState.myIsFoldingOutlineShown
  }

  override fun setFoldingOutlineShown(`val`: Boolean) {
    myState.myIsFoldingOutlineShown = `val`
  }

  override fun isAutoCodeFoldingEnabled(): Boolean {
    return myState.myAutoCodeFoldingEnabled
  }

  override fun setAutoCodeFoldingEnabled(`val`: Boolean) {
    myState.myAutoCodeFoldingEnabled = `val`
  }

  override fun isUseTabCharacter(project: Project): Boolean {
    if (project != myState.project) {
      // todo mb log error only under some flag?
      LOG.error("called isUseTabCharacter with foreign project")  // investigating such cases
      return isUseTabCharacterForForeignProject(project)
    }
    return myState.myUseTabCharacter
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
    myState.myUseTabCharacter = `val`
  }

  fun reinitSettings() {
    for (setting in myComputableSettings) {
      setting.resetCache()
    }
    myState.refreshAll()
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
    if (ApplicationManager.getApplication().isUnitTestMode) {
      computeIndentOptions(project, file).associateWithDocument(document)
    }
    else {
      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
        val result = readAction {
          computeIndentOptions(project, file)
        }
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          result.associateWithDocument(document)
        }
      }.cancelOnDispose(editor.disposable)
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
    return myState.myIsSmartHome
  }

  override fun setSmartHome(`val`: Boolean) {
    myState.myIsSmartHome = `val`
  }

  override fun getVerticalScrollOffset(): Int {
    return myState.myVerticalScrollOffset
  }

  override fun setVerticalScrollOffset(`val`: Int) {
    if (`val` == -1) myState.clearOverriding(myState::myVerticalScrollOffset)
    else myState.myVerticalScrollOffset = `val`
  }

  override fun getVerticalScrollJump(): Int {
    return myState.myVerticalScrollJump
  }

  override fun setVerticalScrollJump(`val`: Int) {
    if (`val` == -1) myState.clearOverriding(myState::myVerticalScrollJump)
    else myState.myVerticalScrollJump = `val`
  }

  override fun getHorizontalScrollOffset(): Int {
    return myState.myHorizontalScrollOffset
  }

  override fun setHorizontalScrollOffset(`val`: Int) {
    if (`val` == -1) myState.clearOverriding(myState::myHorizontalScrollOffset)
    else myState.myHorizontalScrollOffset = `val`
  }

  override fun getHorizontalScrollJump(): Int {
    return myState.myHorizontalScrollJump
  }

  override fun setHorizontalScrollJump(`val`: Int) {
    if (`val` == -1) myState.clearOverriding(myState::myHorizontalScrollJump)
    else myState.myHorizontalScrollJump = `val`
  }

  override fun isVirtualSpace(): Boolean {
    return myState.myIsVirtualSpace
  }

  override fun setVirtualSpace(allow: Boolean) {
    myState.myIsVirtualSpace = allow
  }

  override fun isAdditionalPageAtBottom(): Boolean {
    return myState.myIsAdditionalPageAtBottom
  }

  override fun setAdditionalPageAtBottom(`val`: Boolean) {
    myState.myIsAdditionalPageAtBottom = `val`
  }

  override fun isCaretInsideTabs(): Boolean {
    return myState.myIsCaretInsideTabs
  }

  override fun setCaretInsideTabs(allow: Boolean) {
    myState.myIsCaretInsideTabs = allow
  }

  override fun isBlockCursor(): Boolean {
    return myState.myIsBlockCursor
  }

  override fun setBlockCursor(`val`: Boolean) {
    myState.myIsBlockCursor = `val`
  }

  override fun isFullLineHeightCursor(): Boolean {
    return myState.myIsFullLineHeightCursor
  }

  override fun setFullLineHeightCursor(`val`: Boolean) {
    myState.myIsFullLineHeightCursor = `val`
  }

  override fun isCaretRowShown(): Boolean {
    return myState.myCaretRowShown
  }

  override fun setCaretRowShown(`val`: Boolean) {
    myState.myCaretRowShown = `val`
  }

  override fun getLineCursorWidth(): Int {
    return myState.myLineCursorWidth
  }

  override fun setLineCursorWidth(width: Int) {
    //if (width == myLineCursorWidth) return

    myState.myLineCursorWidth = width
    //myDispatcher.multicaster.lineCursorWidthChanged(width)
  }

  override fun isAnimatedScrolling(): Boolean {
    return myState.myIsAnimatedScrolling
  }

  override fun setAnimatedScrolling(`val`: Boolean) {
    myState.myIsAnimatedScrolling = `val`
  }

  override fun isCamelWords(): Boolean {
    return myState.myIsCamelWords
  }

  override fun setCamelWords(`val`: Boolean) {
    myState.myIsCamelWords = `val`
  }

  override fun resetCamelWords() {
    myState.clearOverriding(myState::myIsCamelWords)
  }

  override fun isBlinkCaret(): Boolean {
    return myState.myIsCaretBlinking
  }

  override fun setBlinkCaret(`val`: Boolean) {
    myState.myIsCaretBlinking = `val`
  }

  override fun getCaretBlinkPeriod(): Int {
    return myState.myCaretBlinkingPeriod
  }

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    myState.myCaretBlinkingPeriod = blinkPeriod
  }

  override fun isDndEnabled(): Boolean {
    return myState.myIsDndEnabled
  }

  override fun setDndEnabled(`val`: Boolean) {
    myState.myIsDndEnabled = `val`
  }

  override fun isWheelFontChangeEnabled(): Boolean {
    return myState.myIsWheelFontChangeEnabled
  }

  override fun setWheelFontChangeEnabled(`val`: Boolean) {
    myState.myIsWheelFontChangeEnabled = `val`
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean {
    return myState.myIsMouseClickSelectionHonorsCamelWords
  }

  override fun setMouseClickSelectionHonorsCamelWords(`val`: Boolean) {
    myState.myIsMouseClickSelectionHonorsCamelWords = `val`
  }

  override fun isVariableInplaceRenameEnabled(): Boolean {
    return myState.myIsRenameVariablesInplace
  }

  override fun setVariableInplaceRenameEnabled(`val`: Boolean) {
    myState.myIsRenameVariablesInplace = `val`
  }

  override fun isRefrainFromScrolling(): Boolean {
    return myState.myIsRefrainFromScrolling
  }

  override fun setRefrainFromScrolling(b: Boolean) {
    myState.myIsRefrainFromScrolling = b
  }

  override fun isUseSoftWraps(): Boolean {
    return myState.myUseSoftWraps
  }

  override fun setUseSoftWraps(use: Boolean) {
    myState.myUseSoftWraps = use
  }

  fun setUseSoftWrapsQuiet() {
    doNotRefreshEditorFlag = true
    try {
      myState.myUseSoftWraps = true
    }
    finally {
      doNotRefreshEditorFlag = false
    }
  }

  override fun isAllSoftWrapsShown(): Boolean {
    return EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown
  }

  override fun isPaintSoftWraps(): Boolean {
    return myState.myPaintSoftWraps
  }

  override fun setPaintSoftWraps(`val`: Boolean) {
    myState.myPaintSoftWraps = `val`
  }

  override fun isUseCustomSoftWrapIndent(): Boolean {
    return myState.myUseCustomSoftWrapIndent
  }

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    myState.myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
  }

  override fun getCustomSoftWrapIndent(): Int {
    return myState.myCustomSoftWrapIndent
  }

  override fun setCustomSoftWrapIndent(indent: Int) {
    myState.myCustomSoftWrapIndent = indent
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean {
    return myState.myAllowSingleLogicalLineFolding
  }

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    myState.myAllowSingleLogicalLineFolding = allow
  }

  private fun fireEditorRefresh(reinitSettings: Boolean = true) {
    editor?.reinitSettings(true, reinitSettings)
  }

  override fun isPreselectRename(): Boolean {
    return myState.myRenamePreselect
  }

  override fun setPreselectRename(`val`: Boolean) {
    myState.myRenamePreselect = `val`
  }

  override fun isShowIntentionBulb(): Boolean {
    return myState.myShowIntentionBulb
  }

  override fun setShowIntentionBulb(show: Boolean) {
    myState.myShowIntentionBulb = show
  }

  val language: Language?
    get() = languageSupplier?.invoke()

  override fun setLanguageSupplier(languageSupplier: Supplier<out Language?>?) {
    this.languageSupplier = languageSupplier?.let {
      @Suppress("SuspiciousCallableReferenceInLambda", "RedundantSuppression")
      it::get
    }
    myState.languageSupplier = languageSupplier?.let {
      @Suppress("SuspiciousCallableReferenceInLambda", "RedundantSuppression")
      it::get
    }
  }

  override fun isShowingSpecialChars(): Boolean {
    return myState.showingSpecialCharacters
  }

  override fun setShowingSpecialChars(value: Boolean) {
    myState.showingSpecialCharacters = value
  }

  override fun getLineNumerationType(): LineNumerationType {
    return myState.myLineNumeration
  }

  override fun setLineNumerationType(value: LineNumerationType) {
    myState.myLineNumeration = value
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun getState(): EditorSettingsState {
    return myState
  }

  private abstract inner class CacheableBackgroundComputable<T>(defaultValue: T) {
    private var overwrittenValue: T? = null
    private var cachedValue: T? = null
    private var defaultValue: T
    private val currentReadActionRef = AtomicReference<Job?>()

    init {
      @Suppress("LeakingThis")
      myComputableSettings.add(this)
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
        @Suppress("DEPRECATION")
        val readJob = (project?.coroutineScope ?: ApplicationManager.getApplication().coroutineScope)
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
              fireEditorRefresh(false)
              if (oldGetValueResult != newGetValueResult) {
                fireValueChanged(newGetValueResult)
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
