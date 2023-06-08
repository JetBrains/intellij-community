// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.EditorSettingsListener
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
import com.intellij.util.EventDispatcher
import com.intellij.util.PatternUtil
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.max

private val LOG = logger<SettingsImpl>()
internal const val EDITOR_SHOW_SPECIAL_CHARS: String = "editor.show.special.chars"

class SettingsImpl internal constructor(private val editor: EditorImpl?, kind: EditorKind?) : EditorSettings {
  private val myDispatcher: EventDispatcher<EditorSettingsListener> = EventDispatcher.create(EditorSettingsListener::class.java)

  private var languageSupplier: (() -> Language?)? = null
  private var myIsCamelWords: Boolean? = null

  // This group of settings does not have a UI
  @get:Deprecated("use {@link EditorKind}")
  var softWrapAppliancePlace: SoftWrapAppliancePlaces
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
  private var myVerticalScrollOffset: Int = -1
  private var myVerticalScrollJump: Int = -1
  private var myHorizontalScrollOffset: Int = -1
  private var myHorizontalScrollJump: Int = -1
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
  private var myLineNumeration: EditorSettings.LineNumerationType? = null

  private val softMargins: CacheableBackgroundComputable<List<Int>> = object : CacheableBackgroundComputable<List<Int>>(emptyList()) {
    override fun computeValue(project: Project?): List<Int> {
      return if (editor == null) emptyList() else CodeStyle.getSettings(editor).getSoftMargins(language)
    }

    override fun fireValueChanged(newValue: List<Int>) {
      myDispatcher.multicaster.softMarginsChanged(newValue)
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

    override fun fireValueChanged(newValue: Int) {
      myDispatcher.multicaster.rightMarginChanged(newValue)
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

    override fun fireValueChanged(newValue: Int) {
      myDispatcher.multicaster.tabSizeChanged(newValue)
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
    if (editor != null && getRightMargin(editor.project) == CodeStyleConstraints.MAX_RIGHT_MARGIN) {
      return false
    }
    else {
      return EditorSettingsExternalizable.getInstance().isRightMarginShown
    }
  }

  override fun setRightMarginShown(`val`: Boolean) {
    if (`val` == myIsRightMarginShown) return

    myIsRightMarginShown = `val`
    myDispatcher.multicaster.isRightMarginShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun isWhitespacesShown(): Boolean {
    return myIsWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isWhitespacesShown
  }

  override fun setWhitespacesShown(`val`: Boolean) {
    if (`val` == myIsWhitespacesShown) return

    myIsWhitespacesShown = `val`
    myDispatcher.multicaster.isWhitespacesShownChanged(`val`)
  }

  override fun isLeadingWhitespaceShown(): Boolean {
    return myIsLeadingWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isLeadingWhitespacesShown
  }

  override fun setLeadingWhitespaceShown(`val`: Boolean) {
    if (`val` == myIsLeadingWhitespacesShown) return

    myIsLeadingWhitespacesShown = `val`
    myDispatcher.multicaster.isLeadingWhitespaceShownChanged(`val`)
  }

  override fun isInnerWhitespaceShown(): Boolean {
    return myIsInnerWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isInnerWhitespacesShown
  }

  override fun setInnerWhitespaceShown(`val`: Boolean) {
    if (`val` == myIsInnerWhitespacesShown) return

    myIsInnerWhitespacesShown = `val`
    myDispatcher.multicaster.isInnerWhitespaceShownChanged(`val`)
  }

  override fun isTrailingWhitespaceShown(): Boolean {
    return myIsTrailingWhitespacesShown ?: EditorSettingsExternalizable.getInstance().isTrailingWhitespacesShown
  }

  override fun setTrailingWhitespaceShown(`val`: Boolean) {
    if (`val` == myIsTrailingWhitespacesShown) return

    myIsTrailingWhitespacesShown = `val`
    myDispatcher.multicaster.isTrailingWhitespaceShownChanged(`val`)
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
    if (`val` == myIsSelectionWhitespacesShown) return

    myIsSelectionWhitespacesShown = `val`
    myDispatcher.multicaster.isSelectionWhitespaceShownChanged(`val`)
  }

  override fun isIndentGuidesShown(): Boolean {
    return myIndentGuidesShown ?: EditorSettingsExternalizable.getInstance().isIndentGuidesShown
  }

  override fun setIndentGuidesShown(`val`: Boolean) {
    if (`val` == myIndentGuidesShown) return

    myIndentGuidesShown = `val`
    myDispatcher.multicaster.isIndentGuidesShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun isLineNumbersShown(): Boolean {
    return myAreLineNumbersShown ?: EditorSettingsExternalizable.getInstance().isLineNumbersShown
  }

  override fun setLineNumbersShown(`val`: Boolean) {
    if (`val` == myAreLineNumbersShown) return

    myAreLineNumbersShown = `val`
    myDispatcher.multicaster.isLineNumbersShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun areGutterIconsShown(): Boolean {
    return myGutterIconsShown ?: EditorSettingsExternalizable.getInstance().areGutterIconsShown()
  }

  override fun setGutterIconsShown(`val`: Boolean) {
    if (`val` == myGutterIconsShown) return

    myGutterIconsShown = `val`
    myDispatcher.multicaster.areGutterIconsShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun getRightMargin(project: Project?): Int = rightMargin.getValue(project)

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
    if (`val` == myWrapWhenTypingReachesRightMargin) return

    myWrapWhenTypingReachesRightMargin = `val`
    myDispatcher.multicaster.isWrapWhenTypingReachesRightMarginChanged(`val`)
  }

  override fun setRightMargin(rightMargin: Int) {
    this.rightMargin.setValue(rightMargin)
    myDispatcher.multicaster.rightMarginChanged(rightMargin)
  }

  override fun getSoftMargins(): List<Int> = softMargins.getValue(null)

  override fun setSoftMargins(softMargins: List<Int>?) {
    this.softMargins.setValue(softMargins?.toList())
    myDispatcher.multicaster.softMarginsChanged(getSoftMargins())
  }

  override fun getAdditionalLinesCount(): Int = myAdditionalLinesCount

  override fun setAdditionalLinesCount(additionalLinesCount: Int) {
    if (myAdditionalLinesCount == additionalLinesCount) return

    
    myAdditionalLinesCount = additionalLinesCount
    myDispatcher.multicaster.additionalLinesCountChanged(additionalLinesCount)
    fireEditorRefresh()
  }

  override fun getAdditionalColumnsCount(): Int {
    return myAdditionalColumnsCount
  }

  override fun setAdditionalColumnsCount(additionalColumnsCount: Int) {
    if (myAdditionalColumnsCount == additionalColumnsCount) return

    
    myAdditionalColumnsCount = additionalColumnsCount
    myDispatcher.multicaster.additionalColumnsCountChanged(additionalColumnsCount)
    fireEditorRefresh()
  }

  override fun isLineMarkerAreaShown(): Boolean {
    return myLineMarkerAreaShown
  }

  override fun setLineMarkerAreaShown(lineMarkerAreaShown: Boolean) {
    if (myLineMarkerAreaShown == lineMarkerAreaShown) return

    myLineMarkerAreaShown = lineMarkerAreaShown
    myDispatcher.multicaster.isLineMarkerAreaShownChanged(lineMarkerAreaShown)
    fireEditorRefresh()
  }

  override fun isFoldingOutlineShown(): Boolean {
    return myIsFoldingOutlineShown ?: EditorSettingsExternalizable.getInstance().isFoldingOutlineShown
  }

  override fun setFoldingOutlineShown(`val`: Boolean) {
    if (`val` == myIsFoldingOutlineShown) return

    myIsFoldingOutlineShown = `val`
    myDispatcher.multicaster.isFoldingOutlineShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun isAutoCodeFoldingEnabled(): Boolean {
    return myAutoCodeFoldingEnabled
  }

  override fun setAutoCodeFoldingEnabled(`val`: Boolean) {
    if (`val` == myAutoCodeFoldingEnabled) return

    myAutoCodeFoldingEnabled = `val`
    myDispatcher.multicaster.isAutoCodeFoldingEnabledChanged(`val`)
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
    if (`val` == myUseTabCharacter) return

    myUseTabCharacter = `val`
    myDispatcher.multicaster.isUseTabCharacterChanged(`val`)
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
    myDispatcher.multicaster.tabSizeChanged(tabSize)
  }

  override fun isSmartHome(): Boolean {
    return myIsSmartHome ?: EditorSettingsExternalizable.getInstance().isSmartHome
  }

  override fun setSmartHome(`val`: Boolean) {
    if (`val` == myIsSmartHome) return

    myIsSmartHome = `val`
    myDispatcher.multicaster.isSmartHomeChanged(`val`)
    fireEditorRefresh()
  }

  override fun getVerticalScrollOffset(): Int {
    return if (myVerticalScrollOffset == -1) EditorSettingsExternalizable.getInstance().verticalScrollOffset else myVerticalScrollOffset
  }

  override fun setVerticalScrollOffset(`val`: Int) {
    if (myVerticalScrollOffset == `val`) return

    myVerticalScrollOffset = `val`
    myDispatcher.multicaster.verticalScrollOffsetChanged(`val`)
    fireEditorRefresh()
  }

  override fun getVerticalScrollJump(): Int {
    return if (myVerticalScrollJump == -1) EditorSettingsExternalizable.getInstance().verticalScrollJump else myVerticalScrollJump
  }

  override fun setVerticalScrollJump(`val`: Int) {
    if (`val` == myVerticalScrollJump) return

    myVerticalScrollJump = `val`
    myDispatcher.multicaster.verticalScrollJumpChanged(`val`)
  }

  override fun getHorizontalScrollOffset(): Int {
    return if (myHorizontalScrollOffset == -1) EditorSettingsExternalizable.getInstance().horizontalScrollOffset else myHorizontalScrollOffset
  }

  override fun setHorizontalScrollOffset(`val`: Int) {
    if (myHorizontalScrollOffset == `val`) return

    myHorizontalScrollOffset = `val`
    myDispatcher.multicaster.horizontalScrollOffsetChanged(`val`)
    fireEditorRefresh()
  }

  override fun getHorizontalScrollJump(): Int {
    return if (myHorizontalScrollJump == -1) EditorSettingsExternalizable.getInstance().horizontalScrollJump else myHorizontalScrollJump
  }

  override fun setHorizontalScrollJump(`val`: Int) {
    if (`val` == myHorizontalScrollJump) return

    myHorizontalScrollJump = `val`
    myDispatcher.multicaster.horizontalScrollJumpChanged(`val`)
  }

  override fun isVirtualSpace(): Boolean {
    if (editor != null && editor.isColumnMode) {
      return true
    }
    return myIsVirtualSpace ?: EditorSettingsExternalizable.getInstance().isVirtualSpace
  }

  override fun setVirtualSpace(allow: Boolean) {
    if (allow == myIsVirtualSpace) return

    myIsVirtualSpace = allow
    myDispatcher.multicaster.isVirtualSpaceChanged(allow)
    fireEditorRefresh()
  }

  override fun isAdditionalPageAtBottom(): Boolean {
    return myIsAdditionalPageAtBottom ?: EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom
  }

  override fun setAdditionalPageAtBottom(`val`: Boolean) {
    if (`val` == myIsAdditionalPageAtBottom) return

    myIsAdditionalPageAtBottom = `val`
    myDispatcher.multicaster.isAdditionalPageAtBottomChanged(`val`)
  }

  override fun isCaretInsideTabs(): Boolean {
    if (editor != null && editor.isColumnMode) return true
    return myIsCaretInsideTabs ?: EditorSettingsExternalizable.getInstance().isCaretInsideTabs
  }

  override fun setCaretInsideTabs(allow: Boolean) {
    if (allow == myIsCaretInsideTabs) return

    myIsCaretInsideTabs = allow
    myDispatcher.multicaster.isCaretInsideTabsChanged(allow)
    fireEditorRefresh()
  }

  override fun isBlockCursor(): Boolean {
    return myIsBlockCursor ?: EditorSettingsExternalizable.getInstance().isBlockCursor
  }

  override fun setBlockCursor(`val`: Boolean) {
    if (`val` == myIsBlockCursor) return

    myIsBlockCursor = `val`
    myDispatcher.multicaster.isBlockCursorChanged(`val`)
    if (editor != null) {
      editor.updateCaretCursor()
      editor.contentComponent.repaint()
    }
  }

  override fun isCaretRowShown(): Boolean {
    return myCaretRowShown ?: EditorSettingsExternalizable.getInstance().isCaretRowShown
  }

  override fun setCaretRowShown(`val`: Boolean) {
    if (`val` == myCaretRowShown) return

    myCaretRowShown = `val`
    myDispatcher.multicaster.isCaretRowShownChanged(`val`)
    fireEditorRefresh()
  }

  override fun getLineCursorWidth(): Int {
    return myLineCursorWidth
  }

  override fun setLineCursorWidth(width: Int) {
    if (width == myLineCursorWidth) return

    myLineCursorWidth = width
    myDispatcher.multicaster.lineCursorWidthChanged(width)
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
    if (`val` == myIsAnimatedScrolling) return

    myIsAnimatedScrolling = `val`
    myDispatcher.multicaster.isAnimatedScrollingChanged(`val`)
  }

  override fun isCamelWords(): Boolean {
    return myIsCamelWords ?: EditorSettingsExternalizable.getInstance().isCamelWords
  }

  override fun setCamelWords(`val`: Boolean) {
    if (`val` == myIsCamelWords) return

    myIsCamelWords = `val`
    myDispatcher.multicaster.isCamelWordsChanged(`val`)
  }

  override fun resetCamelWords() {
    myIsCamelWords = null
  }

  override fun isBlinkCaret(): Boolean {
    return myIsCaretBlinking ?: EditorSettingsExternalizable.getInstance().isBlinkCaret
  }

  override fun setBlinkCaret(`val`: Boolean) {
    if (`val` == myIsCaretBlinking) return

    myIsCaretBlinking = `val`
    myDispatcher.multicaster.isBlinkCaretChanged(`val`)
    fireEditorRefresh()
  }

  override fun getCaretBlinkPeriod(): Int {
    return myCaretBlinkingPeriod ?: EditorSettingsExternalizable.getInstance().blinkPeriod
  }

  override fun setCaretBlinkPeriod(blinkPeriod: Int) {
    if (blinkPeriod == myCaretBlinkingPeriod) return

    myCaretBlinkingPeriod = blinkPeriod
    myDispatcher.multicaster.caretBlinkPeriodChanged(blinkPeriod)
    fireEditorRefresh()
  }

  override fun isDndEnabled(): Boolean {
    return myIsDndEnabled ?: EditorSettingsExternalizable.getInstance().isDndEnabled
  }

  override fun setDndEnabled(`val`: Boolean) {
    if (`val` == myIsDndEnabled) return

    myIsDndEnabled = `val`
    myDispatcher.multicaster.isDndEnabledChanged(`val`)
  }

  override fun isWheelFontChangeEnabled(): Boolean {
    return myIsWheelFontChangeEnabled ?: EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled
  }

  override fun setWheelFontChangeEnabled(`val`: Boolean) {
    if (`val` == myIsWheelFontChangeEnabled) return

    myIsWheelFontChangeEnabled = `val`
    myDispatcher.multicaster.isWheelFontChangeEnabledChanged(`val`)
  }

  override fun isMouseClickSelectionHonorsCamelWords(): Boolean {
    return myIsMouseClickSelectionHonorsCamelWords ?: EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords
  }

  override fun setMouseClickSelectionHonorsCamelWords(`val`: Boolean) {
    if (`val` == myIsMouseClickSelectionHonorsCamelWords) return

    myIsMouseClickSelectionHonorsCamelWords = `val`
    myDispatcher.multicaster.isMouseClickSelectionHonorsCamelWordsChanged(`val`)
  }

  override fun isVariableInplaceRenameEnabled(): Boolean {
    return myIsRenameVariablesInplace ?: EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
  }

  override fun setVariableInplaceRenameEnabled(`val`: Boolean) {
    if (`val` == myIsRenameVariablesInplace) return

    myIsRenameVariablesInplace = `val`
    myDispatcher.multicaster.isVariableInplaceRenameEnabledChanged(`val`)
  }

  override fun isRefrainFromScrolling(): Boolean {
    return myIsRefrainFromScrolling ?: EditorSettingsExternalizable.getInstance().isRefrainFromScrolling
  }

  override fun setRefrainFromScrolling(b: Boolean) {
    if (b == myIsRefrainFromScrolling) return

    myIsRefrainFromScrolling = b
    myDispatcher.multicaster.isRefrainFromScrollingChanged(b)
  }

  @Suppress("DEPRECATION")
  override fun isUseSoftWraps(): Boolean {
    myUseSoftWraps?.let {
      return it
    }

    val softWrapsEnabled = EditorSettingsExternalizable.getInstance().isUseSoftWraps(softWrapAppliancePlace)
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
    if (use == myUseSoftWraps) return

    myUseSoftWraps = use
    myDispatcher.multicaster.isUseSoftWrapsChanged(use)
    fireEditorRefresh()
  }

  fun setUseSoftWrapsQuiet() {
    if (myUseSoftWraps != true) {
      myUseSoftWraps = true
      myDispatcher.multicaster.isUseSoftWrapsChanged(true)
    }
  }

  override fun isAllSoftWrapsShown(): Boolean {
    return EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown
  }

  override fun isPaintSoftWraps(): Boolean {
    return myPaintSoftWraps
  }

  override fun setPaintSoftWraps(`val`: Boolean) {
    if (`val` == myPaintSoftWraps) return

    myPaintSoftWraps = `val`
    myDispatcher.multicaster.isPaintSoftWrapsChanged(`val`)
  }

  override fun isUseCustomSoftWrapIndent(): Boolean {
    return myUseCustomSoftWrapIndent ?: EditorSettingsExternalizable.getInstance().isUseCustomSoftWrapIndent
  }

  override fun setUseCustomSoftWrapIndent(useCustomSoftWrapIndent: Boolean) {
    if (useCustomSoftWrapIndent == myUseCustomSoftWrapIndent) return

    myUseCustomSoftWrapIndent = useCustomSoftWrapIndent
    myDispatcher.multicaster.isUseCustomSoftWrapIndentChanged(useCustomSoftWrapIndent)
  }

  override fun getCustomSoftWrapIndent(): Int {
    return myCustomSoftWrapIndent ?: EditorSettingsExternalizable.getInstance().customSoftWrapIndent
  }

  override fun setCustomSoftWrapIndent(indent: Int) {
    if (indent == myCustomSoftWrapIndent) return

    myCustomSoftWrapIndent = indent
    myDispatcher.multicaster.customSoftWrapIndentChanged(indent)
  }

  override fun isAllowSingleLogicalLineFolding(): Boolean {
    return myAllowSingleLogicalLineFolding
  }

  override fun setAllowSingleLogicalLineFolding(allow: Boolean) {
    if (allow == myAllowSingleLogicalLineFolding) return

    myAllowSingleLogicalLineFolding = allow
    myDispatcher.multicaster.isAllowSingleLogicalLineFoldingChanged(allow)
  }

  private fun fireEditorRefresh(reinitSettings: Boolean = true) {
    editor?.reinitSettings(true, reinitSettings)
  }

  override fun isPreselectRename(): Boolean {
    return myRenamePreselect ?: EditorSettingsExternalizable.getInstance().isPreselectRename
  }

  override fun setPreselectRename(`val`: Boolean) {
    if (`val` == myRenamePreselect) return

    myRenamePreselect = `val`
    myDispatcher.multicaster.isPreselectRenameChanged(`val`)
  }

  override fun isShowIntentionBulb(): Boolean {
    return myShowIntentionBulb ?: EditorSettingsExternalizable.getInstance().isShowIntentionBulb
  }

  override fun setShowIntentionBulb(show: Boolean) {
    if (show == myShowIntentionBulb) return

    myShowIntentionBulb = show
    myDispatcher.multicaster.isShowIntentionBulbChanged(show)
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
      myDispatcher.multicaster.isShowingSpecialCharsChanged(newState)
      fireEditorRefresh()
    }
  }

  override fun getLineNumerationType(): EditorSettings.LineNumerationType {
    return myLineNumeration ?: EditorSettingsExternalizable.getInstance().lineNumeration
  }

  override fun setLineNumerationType(value: LineNumerationType) {
    if (value == myLineNumeration) return

    myLineNumeration = value
    myDispatcher.multicaster.lineNumerationTypeChanged(value)
  }

  override fun isInsertParenthesesAutomatically(): Boolean {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically
  }

  override fun addEditorSettingsListener(listener: EditorSettingsListener, parentDisposable: Disposable) {
    myDispatcher.addListener(listener, parentDisposable)
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
              val isGetValueResultChanged: Boolean
              synchronized(VALUE_LOCK) {
                // `overwrittenValue` dominates `cachedValue`, `cachedValue` dominates `defaultValue`
                isGetValueResultChanged = overwrittenValue == null && cachedValue != result

                cachedValue = result
                defaultValue = result
              }
              if (isGetValueResultChanged) {
                fireValueChanged(result)
                fireEditorRefresh(false)
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

private fun fileNameMatches(fileName: String, globPatterns: String): Boolean {
  return globPatterns.splitToSequence(';')
    .map { it.trim() }
    .filter { !it.isEmpty() }
    .any { PatternUtil.fromMask(it).matcher(fileName).matches() }
}