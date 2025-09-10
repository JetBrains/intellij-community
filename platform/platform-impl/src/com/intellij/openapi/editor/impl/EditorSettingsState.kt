// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.codeWithMe.ClientId
import com.intellij.lang.Language
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl.CODE_STYLE_SETTINGS
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesLanguageSupport
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLineComponent.Companion.EDITOR_LANGUAGE
import com.intellij.openapi.editor.state.CustomOutValueModifier
import com.intellij.openapi.editor.state.ObservableState
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.editor.state.SyncDefaultValueCalculator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentListener
import com.intellij.psi.codeStyle.CodeStyleConstraints
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsListener
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.PatternUtil
import com.intellij.util.SlowOperations
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicReference

/**
 * The basic purposes of this class is:
 * 1) to store properties
 * 2) to update properties automatically for maintaining up-to-date values of these properties all the time (see constructor)
 * 3) to define properties and their updating logic and to interact with them conveniently
 * 4) to allow subscribing to properties changes events
 *
 * The base specificity of almost all the properties represented here is that each property can be in two states:
 * 1) default
 * 2) overridden
 *
 * Meaning:
 * - The "overridden" state means that the current value of the property was set explicitly and this value is fixed.
 *
 * - The "default" state means that the current value of the property should be equal to a result of some fixed calculation logic.
 * To avoid performing this "some fixed calculation logic" on each getter call,
 * a property stores the result of the last such calculation in its cache.
 * To support the equality to an up-to-date result of this "fixed calculation logic" all the time,
 * the class listens to necessary changes in different external sources,
 * and if one of these sources fires a change event and this event potentially can change
 * the result of the "fixed calculation logic", then the corresponding property is recalculated.
 *
 * Keep in mind that there are several exceptions to this rule (i.e., where [CustomOutValueModifier] is used)
 */
@ApiStatus.Internal
@ApiStatus.Experimental
class EditorSettingsState(private val editor: EditorImpl?,
                                   internal val project: Project?,
                                   private val softWrapAppliancePlace: SoftWrapAppliancePlaces) : ObservableState() {
  companion object {
    private val LOG = logger<EditorSettingsState>()
  }

  // This group of settings does not have a UI
  var myAdditionalLinesCount: Int by property(Registry.intValue("editor.virtual.lines", 5))
  var myAdditionalColumnsCount: Int by property(3)
  var myLineCursorWidth by property(EditorUtil.getDefaultCaretWidth())
  var myLineMarkerAreaShown: Boolean by property(true)
  var myAllowSingleLogicalLineFolding: Boolean by property(false)
  var myAutoCodeFoldingEnabled: Boolean by property(true)
  var myAreLineNumbersAfterIcons: Boolean by property { false }

  // These come from CodeStyleSettings.
  var myUseTabCharacter: Boolean by property {
    ReadAction.compute(ThrowableComputable {
      val file = getVirtualFile()

      if (file == null || project == null) {
        CodeStyle.getProjectOrDefaultSettings(project).getIndentOptions(null).USE_TAB_CHARACTER
      }
      else {
        val settings = editor?.getUserData(CODE_STYLE_SETTINGS) ?: CodeStyle.getSettings(project, file)
        SlowOperations.knownIssue("IDEA-333523, EA-914853").use {
          settings.getIndentOptionsByFile(project, file, null).USE_TAB_CHARACTER
        }
      }
    })
  }
  var myWrapWhenTypingReachesRightMargin: Boolean by property {
    val settings = if (editor == null) {
      CodeStyle.getDefaultSettings()
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor)
    }
    settings.isWrapOnTyping(language)
  }
  var softMargins: List<Int> by property {
    if (editor == null) {
      mutableListOf()
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor).getSoftMargins(language)
    }
  }
  var rightMargin: Int by property {
    val settings = if (editor == null) {
      CodeStyle.getProjectOrDefaultSettings(project)
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor)
    }
    settings.getRightMargin(language)
  }
  // todo: I don't know how to listen to changes for the result of `file?.fileType`. Also seems like there is no way to provide
  //   `language ` directly and it has to be calculated indirectly, so it has to be in background, but the current infrastructure is not ready
  //    for such things to be easily implemented...
  // for now this property is managed directly by SettingsImpl, this is done for consistency
  var tabSize: Int by property(CodeStyleSettings.getDefaults().indentOptions.TAB_SIZE)

  // These come from EditorSettingsExternalizable defaults.
  var myIsVirtualSpace: Boolean by property(EditorSettingsExternalizable.getInstance().isVirtualSpace,
                                            SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isVirtualSpace },
                                            CustomOutValueModifier { if (editor != null && editor.isColumnMode) true else it })
  var myIsCaretInsideTabs: Boolean by property(EditorSettingsExternalizable.getInstance().isCaretInsideTabs,
                                               SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isCaretInsideTabs },
                                               CustomOutValueModifier { if (editor != null && editor.isColumnMode) true else it })
  var myIsCaretBlinking: Boolean by property { EditorSettingsExternalizable.getInstance().isBlinkCaret }
  var myCaretBlinkingPeriod: Int by property { EditorSettingsExternalizable.getInstance().blinkPeriod }
  var myIsRightMarginShown: Boolean by property(EditorSettingsExternalizable.getInstance().isRightMarginShown) {
    if (editor != null && rightMargin == CodeStyleConstraints.MAX_RIGHT_MARGIN) false
    else EditorSettingsExternalizable.getInstance().isRightMarginShown
  }
  var myIsHighlightSelectionOccurrences: Boolean by property { EditorSettingsExternalizable.getInstance().isHighlightSelectionOccurrences }
  var myVerticalScrollOffset: Int by property { EditorSettingsExternalizable.getInstance().verticalScrollOffset }


  var myVerticalScrollJump: Int by property { EditorSettingsExternalizable.getInstance().verticalScrollJump }
  var myHorizontalScrollOffset: Int by property { EditorSettingsExternalizable.getInstance().horizontalScrollOffset }
  var myHorizontalScrollJump: Int by property { EditorSettingsExternalizable.getInstance().horizontalScrollJump }
  var myAreLineNumbersShown: Boolean by property { EditorSettingsExternalizable.getInstance().isLineNumbersShown }
  var myGutterIconsShown: Boolean by property { EditorSettingsExternalizable.getInstance().areGutterIconsShown() }
  var myIsFoldingOutlineShown: Boolean by property { EditorSettingsExternalizable.getInstance().isFoldingOutlineShown }
  var myIsSmartHome: Boolean by property { EditorSettingsExternalizable.getInstance().isSmartHome }
  var myIsBlockCursor: Boolean by property { EditorSettingsExternalizable.getInstance().isBlockCursor }
  var myIsFullLineHeightCursor: Boolean by property { EditorSettingsExternalizable.getInstance().isFullLineHeightCursor }
  var myCaretRowShown: Boolean by property { EditorSettingsExternalizable.getInstance().isCaretRowShown }
  var myIsWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isWhitespacesShown }
  var myIsLeadingWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isLeadingWhitespacesShown }
  var myIsInnerWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isInnerWhitespacesShown }
  var myIsTrailingWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isTrailingWhitespacesShown }
  var myIsSelectionWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isSelectionWhitespacesShown }
  var myIndentGuidesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isIndentGuidesShown }
  var myIsAnimatedScrolling: Boolean by property(EditorSettingsExternalizable.getInstance().isSmoothScrolling,
                                                 SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isSmoothScrolling },
                                                 CustomOutValueModifier {
                                                   if (EditorCoreUtil.isTrueSmoothScrollingEnabled()) // [P.Fatin] uses its own interpolation
                                                     EditorSettingsExternalizable.getInstance().isSmoothScrolling
                                                   else it
                                                 })
  var myIsAdditionalPageAtBottom: Boolean by property { EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom }
  var myIsDndEnabled: Boolean by property { EditorSettingsExternalizable.getInstance().isDndEnabled }
  var myIsWheelFontChangeEnabled: Boolean by property { EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled }
  var myIsMouseClickSelectionHonorsCamelWords: Boolean by property { EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords }
  var myIsRenameVariablesInplace: Boolean by property { EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled }
  var myIsRefrainFromScrolling: Boolean by property { EditorSettingsExternalizable.getInstance().isRefrainFromScrolling }
  var myUseSoftWraps: Boolean by property {
    val softWrapsEnabled = EditorSettingsExternalizable.getInstance().isUseSoftWraps(softWrapAppliancePlace)
    if (!softWrapsEnabled || softWrapAppliancePlace != SoftWrapAppliancePlaces.MAIN_EDITOR || editor == null)
      return@property softWrapsEnabled
    val masks = EditorSettingsExternalizable.getInstance().softWrapFileMasks
    if (masks.trim() == "*") return@property true
    val file = FileDocumentManager.getInstance().getFile(editor.document)
    return@property file != null && fileNameMatches(file.name, masks)
  }
  var myPaintSoftWraps: Boolean by property(true)
  var myUseCustomSoftWrapIndent: Boolean by property { EditorSettingsExternalizable.getInstance().isUseCustomSoftWrapIndent }
  var myCustomSoftWrapIndent: Int by property { EditorSettingsExternalizable.getInstance().customSoftWrapIndent }
  var myRenamePreselect: Boolean by property { EditorSettingsExternalizable.getInstance().isPreselectRename }
  var myShowIntentionBulb: Boolean by property { EditorSettingsExternalizable.getInstance().isShowIntentionBulb }
  var myIsCamelWords: Boolean by property { EditorSettingsExternalizable.getInstance().isCamelWords }
  var myLineNumeration: EditorSettings.LineNumerationType by property { EditorSettingsExternalizable.getInstance().lineNumeration }

  var myStickyLinesShown: Boolean by property { EditorSettingsExternalizable.getInstance().areStickyLinesShown() }
  var myStickyLinesShownForLanguage: Boolean by property {
    this.language?.let {
      val lang = project?.service<StickyLinesLanguageSupport>()?.supportedLang(it) ?: it
      EditorSettingsExternalizable.getInstance().areStickyLinesShownFor(lang.id)
    }
    // Return true to avoid the late appearance of the sticky panel.
    // If the actual value for the language is false,
    // the editor removes all lines from the sticky model later
    ?: true
  }
  var myStickyLinesLimit: Int by property { EditorSettingsExternalizable.getInstance().stickyLineLimit }
  var characterGridWidth: Float? by property(null)

  // These come from AdvancedSettings
  var showingSpecialCharacters: Boolean by property { AdvancedSettings.getBoolean(EDITOR_SHOW_SPECIAL_CHARS) }


  internal var languageSupplier: (() -> Language?)? = null
    set(value) {
      field = value
      recalculateLanguage()
    }
  private val calcLangReadActionRef = AtomicReference<Job?>()
  private var language: Language? = null


  init {
    if (editor != null) {
      CodeStyleSettingsManager.getInstance(project).subscribe(CodeStyleSettingsListener {
        if (it.project != project ||
            it.virtualFile != null && it.virtualFile != editor.virtualFile) return@CodeStyleSettingsListener
        refresh(::myUseTabCharacter)
        refresh(::myWrapWhenTypingReachesRightMargin)
        refresh(::softMargins)
        refresh(::rightMargin)
      }, editor.disposable)


      EditorSettingsExternalizable.getInstance().addPropertyChangeListener(
        PropertyChangeListener { propertyChangeEvent: PropertyChangeEvent? ->
          if (propertyChangeEvent == null) return@PropertyChangeListener
          when (propertyChangeEvent.propertyName) {
            EditorSettingsExternalizable.PropNames.PROP_IS_VIRTUAL_SPACE -> refresh(::myIsVirtualSpace)
            EditorSettingsExternalizable.PropNames.PROP_IS_CARET_INSIDE_TABS -> refresh(::myIsCaretInsideTabs)
            EditorSettingsExternalizable.PropNames.PROP_IS_CARET_BLINKING -> refresh(::myIsCaretBlinking)
            EditorSettingsExternalizable.PropNames.PROP_CARET_BLINKING_PERIOD -> refresh(::myCaretBlinkingPeriod)
            EditorSettingsExternalizable.PropNames.PROP_IS_RIGHT_MARGIN_SHOWN -> refresh(::myIsRightMarginShown)

            EditorSettingsExternalizable.PropNames.PROP_VERTICAL_SCROLL_OFFSET -> refresh(::myVerticalScrollOffset)
            EditorSettingsExternalizable.PropNames.PROP_VERTICAL_SCROLL_JUMP -> refresh(::myVerticalScrollJump)
            EditorSettingsExternalizable.PropNames.PROP_HORIZONTAL_SCROLL_OFFSET -> refresh(::myHorizontalScrollOffset)
            EditorSettingsExternalizable.PropNames.PROP_HORIZONTAL_SCROLL_JUMP -> refresh(::myHorizontalScrollJump)
            EditorSettingsExternalizable.PropNames.PROP_ARE_LINE_NUMBERS_SHOWN -> refresh(::myAreLineNumbersShown)
            EditorSettingsExternalizable.PropNames.PROP_ARE_GUTTER_ICONS_SHOWN -> refresh(::myGutterIconsShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_FOLDING_OUTLINE_SHOWN -> refresh(::myIsFoldingOutlineShown)
            EditorSettingsExternalizable.PropNames.PROP_SMART_HOME -> refresh(::myIsSmartHome)
            EditorSettingsExternalizable.PropNames.PROP_IS_BLOCK_CURSOR -> refresh(::myIsBlockCursor)
            EditorSettingsExternalizable.PropNames.PROP_IS_WHITESPACES_SHOWN -> refresh(::myIsWhitespacesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_LEADING_WHITESPACES_SHOWN -> refresh(::myIsLeadingWhitespacesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_INNER_WHITESPACES_SHOWN -> refresh(::myIsInnerWhitespacesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_TRAILING_WHITESPACES_SHOWN -> refresh(::myIsTrailingWhitespacesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_SELECTION_WHITESPACES_SHOWN -> refresh(::myIsSelectionWhitespacesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_INDENT_GUIDES_SHOWN -> refresh(::myIndentGuidesShown)
            EditorSettingsExternalizable.PropNames.PROP_IS_ANIMATED_SCROLLING -> refresh(::myIsAnimatedScrolling)
            EditorSettingsExternalizable.PropNames.PROP_ADDITIONAL_PAGE_AT_BOTTOM -> refresh(::myIsAdditionalPageAtBottom)
            EditorSettingsExternalizable.PropNames.PROP_IS_DND_ENABLED -> refresh(::myIsDndEnabled)
            EditorSettingsExternalizable.PropNames.PROP_IS_WHEEL_FONTCHANGE_ENABLED -> refresh(::myIsWheelFontChangeEnabled)
            EditorSettingsExternalizable.PropNames.PROP_IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS -> refresh(
              ::myIsMouseClickSelectionHonorsCamelWords)
            EditorSettingsExternalizable.PropNames.PROP_RENAME_VARIABLES_INPLACE -> refresh(::myIsRenameVariablesInplace)
            EditorSettingsExternalizable.PropNames.PROP_REFRAIN_FROM_SCROLLING -> refresh(::myIsRefrainFromScrolling)
            EditorSettingsExternalizable.PropNames.PROP_USE_SOFT_WRAPS -> refresh(::myUseSoftWraps)
            EditorSettingsExternalizable.PropNames.PROP_USE_CUSTOM_SOFT_WRAP_INDENT -> refresh(::myUseCustomSoftWrapIndent)
            EditorSettingsExternalizable.PropNames.PROP_CUSTOM_SOFT_WRAP_INDENT -> refresh(::myCustomSoftWrapIndent)
            EditorSettingsExternalizable.PropNames.PROP_PRESELECT_RENAME -> refresh(::myRenamePreselect)
            EditorSettingsExternalizable.PropNames.PROP_SHOW_INTENTION_BULB -> refresh(::myShowIntentionBulb)
            EditorSettingsExternalizable.PropNames.PROP_IS_CAMEL_WORDS -> refresh(::myIsCamelWords)
            EditorSettingsExternalizable.PropNames.PROP_LINE_NUMERATION -> refresh(::myLineNumeration)
            EditorSettingsExternalizable.PropNames.PROP_SHOW_STICKY_LINES -> refresh(::myStickyLinesShown)
            EditorSettingsExternalizable.PropNames.PROP_SHOW_STICKY_LINES_PER_LANGUAGE -> refresh(::myStickyLinesShownForLanguage)
            EditorSettingsExternalizable.PropNames.PROP_STICKY_LINES_LIMIT -> refresh(::myStickyLinesLimit)
          }
        }, editor.disposable)

      project?.getMessageBus()?.connect(editor.disposable)?.subscribe(PsiDocumentListener.TOPIC,
                                                                      PsiDocumentListener { document, _, _ ->
                                                                        if (document == editor.document) {
                                                                          recalculateLanguage()
                                                                        }
                                                                      })

      project?.getMessageBus()?.connect(editor.disposable)?.subscribe(AdvancedSettingsChangeListener.TOPIC,
                                                                      object : AdvancedSettingsChangeListener {
                                                                        override fun advancedSettingChanged(id: String,
                                                                                                            oldValue: Any,
                                                                                                            newValue: Any) {
                                                                          if (id == EDITOR_SHOW_SPECIAL_CHARS) {
                                                                            refresh(::showingSpecialCharacters)
                                                                          }
                                                                        }
                                                                      })

      (editor.state as? ObservableState)?.addPropertyChangeListener(object : ObservableStateListener {
        override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
          when (event.propertyName) {
            EditorState::isColumnMode.name -> {
              refresh(::myIsVirtualSpace)
              refresh(::myIsCaretInsideTabs)
            }
          }
        }
      }, editor.disposable)

      addPropertyChangeListener(object : ObservableStateListener {
        override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
          if (event.propertyName == ::rightMargin.name) {
            refresh(::myIsRightMarginShown)
          }
        }
      })
    }
  }

  private fun getVirtualFile(): VirtualFile? {
    return (editor ?: return null).virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document)
  }

  private fun recalculateLanguage() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      runCatching {
        updateLanguage(languageSupplier?.invoke())
      }.getOrLogException(LOG)
      return
    }

    if (calcLangReadActionRef.get() == null) {
      val readJob = ((project ?: ApplicationManager.getApplication()) as ComponentManagerEx).getCoroutineScope()
        .launch(start = CoroutineStart.LAZY, context = ClientId.coroutineContext()) {
          val result = readAction {
            languageSupplier?.invoke()
          }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + ClientId.coroutineContext()) {
            calcLangReadActionRef.set(null)
            updateLanguage(result)
          }
        }

      if (calcLangReadActionRef.compareAndSet(null, readJob)) {
        editor?.let { readJob.cancelOnDispose(it.disposable) }
        readJob.start()
      }
    }
  }

  private fun updateLanguage(newLanguage: Language?) {
    if (language == newLanguage) return

    language = newLanguage
    onLanguageChanged()
  }

  private fun onLanguageChanged() {
    refresh(::softMargins)
    refresh(::rightMargin)
    refresh(::myStickyLinesShownForLanguage)
    editor?.putUserData(EDITOR_LANGUAGE, language)
  }
}

private fun fileNameMatches(fileName: String, globPatterns: String): Boolean {
  return globPatterns.splitToSequence(';')
    .map { it.trim() }
    .filter { !it.isEmpty() }
    .any { PatternUtil.fromMask(it).matcher(fileName).matches() }
}

private fun getEditorCodeStyleSettingsOrDefaults(editor: EditorImpl): CodeStyleSettings {
  val editorSettings = editor.getUserData(CODE_STYLE_SETTINGS)
  val project = editor.project
  val file = editor.virtualFile
  return if (project != null && file != null) {
    editorSettings ?: CodeStyle.getSettings(project, file)
  }
  else {
    CodeStyle.getDefaultSettings()
  }
}