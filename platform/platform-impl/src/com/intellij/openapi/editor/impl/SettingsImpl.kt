// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleConstraints
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.util.PatternUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.max

private val LOG = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS = "editor.show.special.chars"

class SettingsImpl internal constructor(private val editor: EditorImpl?, kind: EditorKind?) : EditorSettings {
  private var languageSupplier: (() -> Language?)? = null
  private var myIsCamelWords: Boolean? = null

  // This group of settings does not have a UI
  @get:Deprecated("use {@link EditorKind}")
  var softWrapAppliancePlace: SoftWrapAppliancePlaces? = null
  private var myAdditionalLinesCount = Registry.intValue("editor.virtual.lines", 5)
  private var myAdditionalColumnsCount = 3
  private var myLineCursorWidth = EditorUtil.getDefaultCaretWidth()
  private var myLineMarkerAreaShown = true
  private var myAllowSingleLogicalLineFolding = false
  private var myAutoCodeFoldingEnabled = true

  // These come from CodeStyleSettings.
  private var myUseTabCharacter: Boolean? = null

  // These come from EditorSettingsExternalizable defaults.
  private var myIsVirtualSpace: Boolean? = null
  private var myIsCaretInsideTabs: Boolean? = null
  private var myIsCaretBlinking: Boolean? = null
  private var myCaretBlinkingPeriod: Int? = null
  private var myIsRightMarginShown: Boolean? = null
  private var myVerticalScrollOffset: Int? = null
  private var myVerticalScrollJump: Int? = null
  private var myHorizontalScrollOffset: Int? = null
  private var myHorizontalScrollJump: Int? = null
  private var myAreLineNumbersShown: Boolean? = null
  private var myGutterIconsShown: Boolean? = null
  private var myIsFoldingOutlineShown: Boolean? = null
  private var myIsSmartHome: Boolean? = null
  private var myIsBlockCursor: Boolean? = null
  private var myCaretRowShown: Boolean? = null
  private var myIsWhitespacesShown: Boolean? = null
  private var myIsLeadingWhitespacesShown: Boolean? = null
  private var myIsInnerWhitespacesShown: Boolean? = null
  private var myIsTrailingWhitespacesShown: Boolean? = null
  private var myIsSelectionWhitespacesShown: Boolean? = null
  private var myIndentGuidesShown: Boolean? = null
  private var myIsAnimatedScrolling: Boolean? = null
  private var myIsAdditionalPageAtBottom: Boolean? = null
  private var myIsDndEnabled: Boolean? = null
  private var myIsWheelFontChangeEnabled: Boolean? = null
  private var myIsMouseClickSelectionHonorsCamelWords: Boolean? = null
  private var myIsRenameVariablesInplace: Boolean? = null
  private var myIsRefrainFromScrolling: Boolean? = null
  private var myUseSoftWraps: Boolean? = null
  private var myPaintSoftWraps = true
  private var myUseCustomSoftWrapIndent: Boolean? = null
  private var myCustomSoftWrapIndent: Int? = null
  private var myRenamePreselect: Boolean? = null
  private var myWrapWhenTypingReachesRightMargin: Boolean? = null
  private var myShowIntentionBulb: Boolean? = null
  private var showingSpecialCharacters: Boolean? = null
  private val myComputableSettings = ArrayList<CacheableBackgroundComputable<*>>()

  private val myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("EditorSettings", 3)

  private val softMargins: CacheableBackgroundComputable<List<Int>> = object : CacheableBackgroundComputable<List<Int>>(emptyList()) {
    override fun computeValue(project: Project?): List<Int> {
      return if (editor == null) emptyList() else CodeStyle.getSettings(editor).getSoftMargins(language)
    }
  }

  private val rightMargin = object : CacheableBackgroundComputable<Int>(
    CodeStyleSettings.getDefaults().RIGHT_MARGIN) {
    override fun computeValue(project: Project?): Int {
       if (editor != null) {
         return CodeStyle.getSettings(editor).getRightMargin(language)
      }
      else {
         return CodeStyle.getProjectOrDefaultSettings(project).getRightMargin(language)
      }
    }
  }

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
  }

  constructor() : this(null, null)

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
  }

  override fun isRightMarginShown(): Boolean {
    myIsRightMarginShown?.let {
      return it
    }
    if (editor != null && getRightMargin(editor.project!!) == CodeStyleConstraints.MAX_RIGHT_MARGIN) {
      return false
    }
    else {
      return EditorSettingsExternalizable.getInstance().isRightMarginShown
    }
  }

  override fun setRightMarginShown(`val`: Boolean) {
    if (`val` == myIsRightMarginShown) {
      return
    }

    myIsRightMarginShown = `val`
    fireEditorRefresh()
  }

  override fun isWhitespacesShown(): Boolean {
    return myIsWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isWhitespacesShown
  }

  override fun setWhitespacesShown(`val`: Boolean) {
    myIsWhitespacesShown = `val`
  }

  override fun isLeadingWhitespaceShown(): Boolean {
    return myIsLeadingWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isLeadingWhitespacesShown
  }

  override fun setLeadingWhitespaceShown(`val`: Boolean) {
    myIsLeadingWhitespacesShown = `val`
  }

  override fun isInnerWhitespaceShown(): Boolean {
    return myIsInnerWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isInnerWhitespacesShown
  }

  override fun setInnerWhitespaceShown(`val`: Boolean) {
    myIsInnerWhitespacesShown = `val`
  }

  override fun isTrailingWhitespaceShown(): Boolean {
    return myIsTrailingWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isTrailingWhitespacesShown
  }

  override fun setTrailingWhitespaceShown(`val`: Boolean) {
    myIsTrailingWhitespacesShown = `val`
  }

  override fun isSelectionWhitespaceShown(): Boolean {
    if (myIsSelectionWhitespacesShown != null) {
      return myIsSelectionWhitespacesShown!!
    }
    else {
      return EditorSettingsExternalizable.getInstance().isSelectionWhitespacesShown
    }
  }

  override fun setSelectionWhitespaceShown(`val`: Boolean) {
    myIsSelectionWhitespacesShown = `val`
  }

  override fun isIndentGuidesShown(): Boolean {
    return myIndentGuidesShown ?: EditorSettingsExternalizable.getInstance().isIndentGuidesShown
  }

  override fun setIndentGuidesShown(`val`: Boolean) {
    if (`val` == myIndentGuidesShown) {
      return
    }

    myIndentGuidesShown = `val`
    fireEditorRefresh()
  }

  override fun isLineNumbersShown(): Boolean {
    return myAreLineNumbersShown ?: EditorSettingsExternalizable.getInstance().isLineNumbersShown
  }

  override fun setLineNumbersShown(`val`: Boolean) {
    if (`val` == myAreLineNumbersShown) {
      return
    }

    myAreLineNumbersShown = `val`
    fireEditorRefresh()
  }

  override fun areGutterIconsShown(): Boolean {
    return myGutterIconsShown ?: EditorSettingsExternalizable.getInstance().areGutterIconsShown()
  }

  override fun setGutterIconsShown(`val`: Boolean) {
    if (`val` == myGutterIconsShown) {
      return
    }

    myGutterIconsShown = `val`
    fireEditorRefresh()
  }

  override fun getRightMargin(project: Project): Int = rightMargin.getValue(project)

  override fun isWrapWhenTypingReachesRightMargin(project: Project): Boolean {
    myWrapWhenTypingReachesRightMargin?.let {
      return it
    }
    if (editor == null) {
      return CodeStyle.getDefaultSettings().isWrapOnTyping(language)
    }
    else {
      return CodeStyle.getSettings(editor).isWrapOnTyping(language)
    }
  }

  override fun setWrapWhenTypingReachesRightMargin(`val`: Boolean) {
    myWrapWhenTypingReachesRightMargin = `val`
  }

  override fun setRightMargin(rightMargin: Int) {
    this.rightMargin.setValue(rightMargin)
  }

  override fun getSoftMargins(): List<Int> = softMargins.getValue(null)

  override fun setSoftMargins(softMargins: List<Int>?) {
    this.softMargins.setValue(softMargins?.toList())
  }

  override fun getAdditionalLinesCount(): Int = myAdditionalLinesCount

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    if (myAdditionalLinesCount == additionalLinesCount) {
      return
    }
    myAdditionalLinesCount = additionalLinesCount
    fireEditorRefresh()
  }

  override fun getAdditionalColumnsCount(): Int {
    return myAdditionalColumnsCount
  }

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    if (myAdditionalColumnsCount == additionalColumnsCount) {
      return
    }
    myAdditionalColumnsCount = additionalColumnsCount
    fireEditorRefresh()
  }

  override fun isLineMarkerAreaShown(): Boolean {
    return myLineMarkerAreaShown
  }

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    if (myLineMarkerAreaShown == lineMarkerAreaShown) {
      return
    }
    myLineMarkerAreaShown = lineMarkerAreaShown
    fireEditorRefresh()
  }

  override fun isFoldingOutlineShown(): Boolean {
    return myIsFoldingOutlineShown ?: EditorSettingsExternalizable.getInstance().isFoldingOutlineShown
  }

  override fun setFoldingOutlineShown(`val`: Boolean) {
    if (`val` == myIsFoldingOutlineShown) {
      return
    }

    myIsFoldingOutlineShown = `val`
    fireEditorRefresh()
  }

  override fun isAutoCodeFoldingEnabled(): Boolean {
    return myAutoCodeFoldingEnabled
  }

  override fun setAutoCodeFoldingEnabled(`val`: Boolean) {
    myAutoCodeFoldingEnabled = `val`
  }

  override fun isUseTabCharacter(project: Project): Boolean {
    myUseTabCharacter?.let {
      return it
    }

    val file = getVirtualFile()
    if (file == null) {
      return CodeStyle.getProjectOrDefaultSettings(project).getIndentOptions(null).USE_TAB_CHARACTER
    }
    else {
      return CodeStyle.getIndentOptions(project, file).USE_TAB_CHARACTER
    }
  }

  override fun setUseTabCharacter(`val`: Boolean) {
    if (`val` == myUseTabCharacter) {
      return
    }

    myUseTabCharacter = `val`
    fireEditorRefresh()
  }

  fun reinitSettings() {
    for (setting in myComputableSettings) {
      setting.resetCache()
    }
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
      ReadAction.nonBlocking<CommonCodeStyleSettings.IndentOptions> {
        computeIndentOptions(project, file)
      }.expireWhen { editor.isDisposed || project.isDisposed }.finishOnUiThread(
        ModalityState.any()
      ) { result -> result.associateWithDocument(document) }.submit(myExecutor)
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
    return myIsSmartHome ?: EditorSettingsExternalizable.getInstance().isSmartHome
  }

  override fun setSmartHome(`val`: Boolean) {
    if (`val` == myIsSmartHome) {
      return
    }

    myIsSmartHome = `val`
    fireEditorRefresh()
  }

  override fun getVerticalScrollOffset(): Int {
    return myVerticalScrollOffset ?: EditorSettingsExternalizable.getInstance().verticalScrollOffset
  }

  override fun setVerticalScrollOffset(`val`: Int) {
    if (myVerticalScrollOffset == `val`) {
      return
    }
    myVerticalScrollOffset = `val`
    fireEditorRefresh()
  }

  override fun getVerticalScrollJump(): Int {
    return myVerticalScrollJump ?: EditorSettingsExternalizable.getInstance().verticalScrollJump
  }

  override fun setVerticalScrollJump(`val`: Int) {
    myVerticalScrollJump = `val`
  }

  override fun getHorizontalScrollOffset(): Int {
    return myHorizontalScrollOffset ?: EditorSettingsExternalizable.getInstance().horizontalScrollOffset
  }

  override fun setHorizontalScrollOffset(`val`: Int) {
    if (myHorizontalScrollOffset == `val`) {
      return
    }
    myHorizontalScrollOffset = `val`
    fireEditorRefresh()
  }

  override fun getHorizontalScrollJump(): Int {
    return myHorizontalScrollJump ?: EditorSettingsExternalizable.getInstance().horizontalScrollJump
  }

  override fun setHorizontalScrollJump(`val`: Int) {
    myHorizontalScrollJump = `val`
  }

  override fun isVirtualSpace(): Boolean {
    if (editor != null && editor.isColumnMode) {
      return true
    }
    return myIsVirtualSpace ?: EditorSettingsExternalizable.getInstance().isVirtualSpace
  }

  override fun setVirtualSpace(allow: Boolean) {
    if (allow == myIsVirtualSpace) {
      return
    }
    myIsVirtualSpace = allow
    fireEditorRefresh()
  }

  override fun isAdditionalPageAtBottom(): Boolean {
    return myIsAdditionalPageAtBottom ?: EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom
  }

  override fun setAdditionalPageAtBottom(`val`: Boolean) {
    myIsAdditionalPageAtBottom = `val`
  }

  override fun isCaretInsideTabs(): Boolean {
    if (editor != null && editor.isColumnMode) return true
    return myIsCaretInsideTabs ?: EditorSettingsExternalizable.getInstance().isCaretInsideTabs
  }

  override fun setCaretInsideTabs(allow: Boolean) {
    if (allow == myIsCaretInsideTabs) return
    myIsCaretInsideTabs = allow
    fireEditorRefresh()
  }

  override fun isBlockCursor(): Boolean {
    return myIsBlockCursor ?: EditorSettingsExternalizable.getInstance().isBlockCursor
  }

  override fun setBlockCursor(`val`: Boolean) {
    if (`val` == myIsBlockCursor) {
      return
    }

    myIsBlockCursor = `val`
    if (editor != null) {
      editor.updateCaretCursor()
      editor.contentComponent.repaint()
    }
  }

  override fun isCaretRowShown(): Boolean {
    return myCaretRowShown ?: EditorSettingsExternalizable.getInstance().isCaretRowShown
  }

  override fun setCaretRowShown(`val`: Boolean) {
    if (`val` == myCaretRowShown) {
      return
    }

    myCaretRowShown = `val`
    fireEditorRefresh()
  }

  override fun getLineCursorWidth(): Int {
    return myLineCursorWidth
  }

  override fun setLineCursorWidth(width: Int) {
    myLineCursorWidth = width
  }

  override fun isAnimatedScrolling(): Boolean {
    // uses its own interpolation
    if (myIsAnimatedScrolling == null || EditorCoreUtil.isTrueSmoothScrollingEnabled()) {
      return EditorSettingsExternalizable.getInstance().isSmoothScrolling
    }
    else {
      return myIsAnimatedScrolling!!
    }
  }

  override fun setAnimatedScrolling(`val`: Boolean) {
    myIsAnimatedScrolling = `val`
  }

  override fun isCamelWords(): Boolean {
    return myIsCamelWords ?: EditorSettingsExternalizable.getInstance().isCamelWords
  }

  override fun setCamelWords(`val`: Boolean) {
    myIsCamelWords = `val`
  }

  override fun resetCamelWords() {
    myIsCamelWords = null
  }

  override fun isBlinkCaret(): Boolean {
    return myIsCaretBlinking ?: EditorSettingsExternalizable.getInstance().isBlinkCaret
  }

  override fun setBlinkCaret(`val`: Boolean) {
    if (`val` == myIsCaretBlinking) {
      return
    }

    myIsCaretBlinking = `val`
    fireEditorRefresh()
  }

  override fun getCaretBlinkPeriod(): Int {
    return myCaretBlinkingPeriod ?: EditorSettingsExternalizable.getInstance().blinkPeriod
  }

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    if (blinkPeriod == myCaretBlinkingPeriod) {
      return
    }

    myCaretBlinkingPeriod = blinkPeriod
    fireEditorRefresh()
  }

  override fun isDndEnabled(): Boolean {
    return myIsDndEnabled ?: EditorSettingsExternalizable.getInstance().isDndEnabled
  }

  override fun setDndEnabled(`val`: Boolean) {
    myIsDndEnabled = `val`
  }

  override fun isWheelFontChangeEnabled(): Boolean {
    return myIsWheelFontChangeEnabled ?: EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled
  }

  override fun setWheelFontChangeEnabled(`val`: Boolean) {
    myIsWheelFontChangeEnabled = `val`
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean {
    return myIsMouseClickSelectionHonorsCamelWords ?: EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords
  }

  override fun setMouseClickSelectionHonorsCamelWords(`val`: Boolean) {
    myIsMouseClickSelectionHonorsCamelWords = `val`
  }

  override fun isVariableInplaceRenameEnabled(): Boolean {
    return myIsRenameVariablesInplace ?: EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
  }

  override fun setVariableInplaceRenameEnabled(`val`: Boolean) {
    myIsRenameVariablesInplace = `val`
  }

  override fun isRefrainFromScrolling(): Boolean {
    return myIsRefrainFromScrolling ?: EditorSettingsExternalizable.getInstance().isRefrainFromScrolling
  }

  override fun setRefrainFromScrolling(b: Boolean) {
    myIsRefrainFromScrolling = b
  }

  @Suppress("DEPRECATION")
  override fun isUseSoftWraps(): Boolean {
    myUseSoftWraps?.let {
      return it
    }

    val softWrapsEnabled = EditorSettingsExternalizable.getInstance().isUseSoftWraps(softWrapAppliancePlace!!)
    if (!softWrapsEnabled || softWrapAppliancePlace != SoftWrapAppliancePlaces.MAIN_EDITOR || editor == null) {
      return softWrapsEnabled
    }

    val masks = EditorSettingsExternalizable.getInstance().softWrapFileMasks
    if (masks.trim() == "*") {
      return true
    }

    val file = FileDocumentManager.getInstance().getFile(editor.document)
    return file != null && fileNameMatches(file.name, masks)
  }

  override fun setUseSoftWraps(use: Boolean) {
    if (use == myUseSoftWraps) {
      return
    }

    myUseSoftWraps = use
    fireEditorRefresh()
  }

  fun setUseSoftWrapsQuiet() {
    myUseSoftWraps = true
  }

  override fun isAllSoftWrapsShown(): Boolean {
    return EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown
  }

  override fun isPaintSoftWraps(): Boolean {
    return myPaintSoftWraps
  }

  override fun setPaintSoftWraps(`val`: Boolean) {
    myPaintSoftWraps = `val`
  }

  override fun isUseCustomSoftWrapIndent(): Boolean {
    return myUseCustomSoftWrapIndent ?: EditorSettingsExternalizable.getInstance().isUseCustomSoftWrapIndent
  }

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
  }

  override fun getCustomSoftWrapIndent(): Int {
    return myCustomSoftWrapIndent ?: EditorSettingsExternalizable.getInstance().customSoftWrapIndent
  }

  override fun setCustomSoftWrapIndent(indent: Int) {
    myCustomSoftWrapIndent = indent
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean {
    return myAllowSingleLogicalLineFolding
  }

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    myAllowSingleLogicalLineFolding = allow
  }

  private fun fireEditorRefresh(reinitSettings: Boolean = true) {
    editor?.reinitSettings(true, reinitSettings)
  }

  override fun isPreselectRename(): Boolean {
    return myRenamePreselect ?: EditorSettingsExternalizable.getInstance().isPreselectRename
  }

  override fun setPreselectRename(`val`: Boolean) {
    myRenamePreselect = `val`
  }

  override fun isShowIntentionBulb(): Boolean {
    return myShowIntentionBulb ?: EditorSettingsExternalizable.getInstance().isShowIntentionBulb
  }

  override fun setShowIntentionBulb(show: Boolean) {
    myShowIntentionBulb = show
  }

  val language: Language?
    get() = languageSupplier?.invoke()

  override fun setLanguageSupplier(languageSupplier: Supplier<out Language?>?) {
    this.languageSupplier = languageSupplier?.let {
      @Suppress("SuspiciousCallableReferenceInLambda", "RedundantSuppression")
      it::get
    }
  }

  override fun isShowingSpecialChars(): Boolean {
    return showingSpecialCharacters ?: getBoolean(EDITOR_SHOW_SPECIAL_CHARS)
  }

  override fun setShowingSpecialChars(value: Boolean) {
    val oldState = isShowingSpecialChars
    showingSpecialCharacters = value
    val newState = isShowingSpecialChars
    if (newState != oldState) {
      fireEditorRefresh()
    }
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically
  }

  private abstract inner class CacheableBackgroundComputable<T>(defaultValue: T) {
    private var overwrittenValue: T? = null
    private var cachedValue: T? = null
    private var defaultValue: T
    private val currentReadActionRef = AtomicReference<NonBlockingReadAction<T>?>()

    init {
      @Suppress("LeakingThis")
      myComputableSettings.add(this)
      this.defaultValue = defaultValue
    }

    fun setValue(overwrittenValue: T?) {
      synchronized(VALUE_LOCK) {
        if (this.overwrittenValue == overwrittenValue) {
          return
        }
        this.overwrittenValue = overwrittenValue
      }
      fireEditorRefresh()
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
    private fun getDefaultAndCompute(project: Project?): T {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        runCatching {
          computeValue(project)
        }.getOrLogException(LOG)
      }
      else {
        if (currentReadActionRef.get() == null) {
          val readAction = ReadAction
            .nonBlocking<T> { computeValue(project) }
            .finishOnUiThread(
              ModalityState.any()
            ) { result: T ->
              currentReadActionRef.set(null)
              synchronized(VALUE_LOCK) {
                cachedValue = result
                defaultValue = result
              }
              fireEditorRefresh(false)
            }
            .expireWhen { editor != null && editor.isDisposed || project != null && project.isDisposed }
          if (currentReadActionRef.compareAndSet(null, readAction)) {
            readAction.submit(myExecutor)
          }
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

private fun fileNameMatches(fileName: String, globPatterns: String): Boolean {
  return globPatterns.splitToSequence(';')
    .map { it.trim() }
    .filter { !it.isEmpty() }
    .any { PatternUtil.fromMask(it).matcher(fileName).matches() }
}