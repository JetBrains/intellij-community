// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.actions.CaretStopOptions
import com.intellij.openapi.editor.ex.BidiTextDirection
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl.CODE_STYLE_SETTINGS
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesLanguageSupport
import com.intellij.openapi.editor.state.CustomOutValueModifier
import com.intellij.openapi.editor.state.ObservableState
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.editor.state.StateProperty
import com.intellij.openapi.editor.state.SyncDefaultValueCalculator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleConstraints
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsListener
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.util.PatternUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import kotlin.math.max

/**
 * The settings of one editor, as observable properties. [SettingsImpl] reads and writes them, and the Remote Development
 * backend sends them to the frontend.
 *
 * Each property has a default, and it can have an override. A setter stores an override, and the value then stays
 * fixed until [clearOverriding]. Without an override, the property holds the result of its calculator. The calculator
 * runs again when a source of the default changes, and [subscribeToSources] subscribes to those sources. A
 * [CustomOutValueModifier] can change the value in both cases.
 *
 * A calculator must not take the RW lock, because an editor can be built on `Dispatchers.UI`, which forbids that lock
 * (IJPL-243574). A default that needs the PSI is an input instead, and a [BackgroundStateInput] computes it.
 *
 * The property names are the keys that the backend sends. So a rename must also change the frontend.
 */
@ApiStatus.Internal
class EditorSettingsState(
  private val editor: EditorImpl?,
  internal val project: Project?,
  private val softWrapAppliancePlace: SoftWrapAppliancePlaces,
) : ObservableState() {

  // Settings without a UI. Each default is fixed when the state is built.
  var myAdditionalLinesCount: Int by property(Registry.intValue("editor.virtual.lines", 5))
  var myAdditionalColumnsCount: Int by property(3)
  var myLineCursorWidth: Int by property(EditorUtil.getDefaultCaretWidth())
  var myLineMarkerAreaShown: Boolean by property(true)
  var myAllowSingleLogicalLineFolding: Boolean by property(false)
  var myAutoCodeFoldingEnabled: Boolean by property(true)
  var myAreLineNumbersAfterIcons: Boolean by property(false)
  var rtlEnabled: Boolean by property(!Registry.`is`("editor.disable.rtl", false))

  // Settings from the code style.

  /**
   * Read this value through [currentUseTabCharacter]. In unit-test mode, that function computes the value, and a direct
   * read does not.
   */
  var myUseTabCharacter: Boolean by codeStyleProperty(projectFileIndent().useTabCharacter) { fileIndent.useTabCharacter }

  /**
   * Read this value through [currentTabSize]. In unit-test mode, that function computes the value, and a direct read
   * does not.
   */
  var tabSize: Int by codeStyleProperty(projectFileIndent().tabSize) { fileIndent.tabSize }

  var myWrapWhenTypingReachesRightMargin: Boolean by codeStyleProperty(initialMargins().wrapOnTyping) { margins.wrapOnTyping }
  var softMargins: List<Int> by codeStyleProperty(initialMargins().softMargins) { margins.softMargins }
  var rightMargin: Int by codeStyleProperty(initialMargins().rightMargin) { margins.rightMargin }

  // Settings from EditorSettingsExternalizable. [refreshFromEditorSettings] maps its change events to these properties.
  var myIsVirtualSpace: Boolean by modifiedProperty(trueInColumnMode()) { externalizable().isVirtualSpace }
  var myIsCaretInsideTabs: Boolean by modifiedProperty(trueInColumnMode()) { externalizable().isCaretInsideTabs }
  var myIsCaretBlinking: Boolean by property { externalizable().isBlinkCaret }
  var myCaretBlinkingPeriod: Int by property { externalizable().blinkPeriod }
  var myIsSmoothCaretBlinking: Boolean by property { externalizable().isSmoothBlinkCaret }
  var myIsRightMarginShown: Boolean by property(externalizable().isRightMarginShown) {
    // A right margin at the maximum means that there is no margin.
    if (editor != null && rightMargin == CodeStyleConstraints.MAX_RIGHT_MARGIN) {
      false
    } else {
      externalizable().isRightMarginShown
    }
  }
  var myIsHighlightSelectionOccurrences: Boolean by property { externalizable().isHighlightSelectionOccurrences }
  var myVerticalScrollOffset: Int by property { externalizable().verticalScrollOffset }
  var myVerticalScrollJump: Int by property { externalizable().verticalScrollJump }
  var myHorizontalScrollOffset: Int by property { externalizable().horizontalScrollOffset }
  var myHorizontalScrollJump: Int by property { externalizable().horizontalScrollJump }
  var myAreLineNumbersShown: Boolean by property { externalizable().isLineNumbersShown }
  var myGutterIconsShown: Boolean by property { externalizable().areGutterIconsShown() }
  var myIsFoldingOutlineShown: Boolean by property { externalizable().isFoldingOutlineShown }
  var myIsSmartHome: Boolean by property { externalizable().isSmartHome }
  var myIsBlockCursor: Boolean by property { externalizable().isBlockCursor }
  var myIsFullLineHeightCursor: Boolean by property { externalizable().isFullLineHeightCursor }
  var myIsSmoothCaretMovement: Boolean by property { externalizable().isSmoothCaretMovement }
  var myCaretEasing: EditorSettings.CaretEasing by property { externalizable().caretEasing }
  var myCaretRowShown: Boolean by property { externalizable().isCaretRowShown }
  var myIsWhitespacesShown: Boolean by property { externalizable().isWhitespacesShown }
  var myIsLeadingWhitespacesShown: Boolean by property { externalizable().isLeadingWhitespacesShown }
  var myIsInnerWhitespacesShown: Boolean by property { externalizable().isInnerWhitespacesShown }
  var myIsTrailingWhitespacesShown: Boolean by property { externalizable().isTrailingWhitespacesShown }
  var myIsSelectionWhitespacesShown: Boolean by property { externalizable().isSelectionWhitespacesShown }
  var myIndentGuidesShown: Boolean by property { externalizable().isIndentGuidesShown }
  var myIsAnimatedScrolling: Boolean by modifiedProperty(smoothScrollingWithOwnInterpolation()) { externalizable().isSmoothScrolling }
  var myIsAdditionalPageAtBottom: Boolean by property { externalizable().isAdditionalPageAtBottom }
  var myIsDndEnabled: Boolean by property { externalizable().isDndEnabled }
  var myIsWheelFontChangeEnabled: Boolean by property { externalizable().isWheelFontChangeEnabled }
  var myIsMouseClickSelectionHonorsCamelWords: Boolean by property { externalizable().isMouseClickSelectionHonorsCamelWords }
  var myIsRenameVariablesInplace: Boolean by property { externalizable().isVariableInplaceRenameEnabled }
  var myIsRefrainFromScrolling: Boolean by property { externalizable().isRefrainFromScrolling }
  var myUseSoftWraps: Boolean by property { computeUseSoftWraps() }
  var myPaintSoftWraps: Boolean by property(true)
  var myUseCustomSoftWrapIndent: Boolean by property { externalizable().isUseCustomSoftWrapIndent }
  var myCustomSoftWrapIndent: Int by property { externalizable().customSoftWrapIndent }
  var myRenamePreselect: Boolean by property { externalizable().isPreselectRename }
  var myShowIntentionBulb: Boolean by property { externalizable().isShowIntentionBulb }
  var myIsCamelWords: Boolean by property { externalizable().isCamelWords }
  var myLineNumeration: EditorSettings.LineNumerationType by property { externalizable().lineNumeration }
  var myStickyLinesShown: Boolean by property { externalizable().areStickyLinesShown() }
  var myStickyLinesShownForLanguage: Boolean by property { computeStickyLinesShownForLanguage() }
  var myStickyLinesLimit: Int by property { externalizable().stickyLineLimit }
  var characterGridWidth: Float? by property(null)

  // Global-only settings from EditorSettingsExternalizable. A `val` has no override, so it always holds the global value.
  val foldingOutlineShownOnlyOnHover: Boolean by property { externalizable().isFoldingOutlineShownOnlyOnHover }
  val foldingEndingsShown: Boolean by property { externalizable().isFoldingEndingsShown }
  val wheelFontChangePersistent: Boolean by property { externalizable().isWheelFontChangePersistent }
  val addCaretsOnDoubleCtrl: Boolean by property { externalizable().addCaretsOnDoubleCtrl() }
  val allSoftWrapsShown: Boolean by property { externalizable().isAllSoftWrapsShown }
  val insertParenthesesAutomatically: Boolean by property { externalizable().isInsertParenthesesAutomatically }
  val showInspectionWidget: Boolean by property { externalizable().isShowInspectionWidget }
  val useEditorFontInInlays: Boolean by property { externalizable().isUseEditorFontInInlays }
  val bidiTextDirection: BidiTextDirection by property { externalizable().bidiTextDirection }
  val caretStopOptions: CaretStopOptions by property { externalizable().caretStopOptions }

  // Settings from AdvancedSettings. `EditorFactoryImpl` refreshes all editors when this setting changes.
  var showingSpecialCharacters: Boolean by property { AdvancedSettings.getBoolean(EDITOR_SHOW_SPECIAL_CHARS) }

  /**
   * Gives the language of the editor file. A new supplier and [recalculateInputs] compute the language again.
   */
  internal var languageSupplier: (() -> Language?)? = null
    set(value) {
      field = value
      languageInput.recalculate()
    }

  /**
   * The tab size that the editor uses.
   *
   * When the inputs compute in the background, a call returns [tabSize]. [SettingsImpl] starts the first computation of
   * [fileIndent] when it is built, so a call starts nothing.
   *
   * When the inputs compute synchronously, as in unit-test mode, the state keeps no cached value. A call returns the
   * override, or it computes the value on the calling thread. A test can change the code style without an event, and the
   * editor must see the new value at once.
   */
  internal fun currentTabSize(): Int {
    if (!BackgroundStateInput.computesInBackground()) {
      if (isOverridden(::tabSize)) {
        return tabSize
      }
      return computeFileIndentForTest().tabSize
    }
    return tabSize
  }

  /**
   * The tab character setting that the editor uses. The rules of [currentTabSize] apply.
   */
  internal fun currentUseTabCharacter(): Boolean {
    if (!BackgroundStateInput.computesInBackground()) {
      if (isOverridden(::myUseTabCharacter)) {
        return myUseTabCharacter
      }
      return computeFileIndentForTest().useTabCharacter
    }
    return myUseTabCharacter
  }

  /**
   * Computes the inputs again: the language, [margins], and [fileIndent]. Then it refreshes the properties that read them.
   *
   * [SettingsImpl] calls this when it is built, and [SettingsImpl.reinitSettings] calls this after each event that can
   * change the inputs. These events are a code style change, a new file, and a new highlighter. `EditorHighlighterUpdater`
   * sets a new highlighter when the file type changes or the file gets a new name.
   */
  internal fun recalculateInputs() {
    if (languageSupplier != null) {
      languageInput.recalculate()
    }
    marginsInput.recalculate()
    fileIndentInput.recalculate()
  }

  /**
   * Waits until the background computations of the inputs end.
   */
  @TestOnly
  internal suspend fun awaitInputsForTests() {
    languageInput.awaitForTests()
    marginsInput.awaitForTests()
    fileIndentInput.awaitForTests()
  }

  /**
   * The language of the editor file, which [languageInput] computes.
   *
   * [initialMargins] reads it while the state is built, before this field gets its value. It sees `null` then, which is
   * also the value before the first computation.
   */
  private var language: Language? = null

  /**
   * The margins that the code style gives the editor file in its language. [myWrapWhenTypingReachesRightMargin],
   * [softMargins], and [rightMargin] calculate their defaults from it.
   *
   * The code style settings of a file can need a read action, so [marginsInput] computes the value in the background.
   */
  private var margins: Margins = initialMargins()

  /**
   * The indent values that the code style gives the editor file. [myUseTabCharacter] and [tabSize] calculate their
   * defaults from it.
   *
   * The per-file value needs the PSI, and therefore the RW lock, so a calculator must not compute it. [fileIndentInput]
   * computes the value in a background read action, and then refreshes the two properties.
   *
   * The seed is the project setting, and not the global one. It is what the Remote Development frontend reads until the
   * backend pushes the computed value, and this value selects the character an indent writes. The project setting needs
   * no lock, and it is right for every file that no `FileIndentOptionsProvider` overrides.
   */
  private var fileIndent: FileIndent = projectFileIndent()

  private val languageInput: BackgroundStateInput<Language?> = BackgroundStateInput(
    scope = EditorCoroutineScopes.settingsScope(editor, project),
    compute = { languageSupplier?.invoke() },
    update = ::updateLanguage,
  )

  private val marginsInput: BackgroundStateInput<Margins> = BackgroundStateInput(
    scope = EditorCoroutineScopes.settingsScope(editor, project),
    compute = { computeMargins() },
    update = ::updateMargins,
  )

  private val fileIndentInput: BackgroundStateInput<FileIndent> = BackgroundStateInput(
    scope = EditorCoroutineScopes.settingsScope(editor, project),
    compute = { computeFileIndent(ignoreDocOptions = true) },
    update = ::updateFileIndent,
  )

  init {
    if (editor != null) {
      subscribeToSources(editor)
    }
  }

  /**
   * Subscribes to each source of a default, for the life of [editor].
   */
  private fun subscribeToSources(editor: EditorImpl) {
    @Suppress("DEPRECATION")
    val disposable = editor.disposable
    if (project == null) {
      subscribeToCodeStyle(disposable)
    }
    externalizable().addPropertyChangeListener(
      PropertyChangeListener { event: PropertyChangeEvent? ->
        if (event != null) {
          refreshFromEditorSettings(event.propertyName)
        }
      },
      disposable,
    )
    subscribeToFileTypes(disposable)
    subscribeToEditorState(editor, disposable)
    addPropertyChangeListener(
      object : ObservableStateListener {
        override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
          if (event.propertyName == ::rightMargin.name) {
            refresh(::myIsRightMarginShown)
          }
        }
      }
    )
  }

  /**
   * Follows the default code style, for an editor without a project. `EditorImpl.codeStyleSettingsChanged` handles an
   * editor with a project: it first stores the new settings of the file, and then reinitializes the settings.
   */
  private fun subscribeToCodeStyle(disposable: Disposable) {
    CodeStyleSettingsManager.getInstance(null).subscribe(
      CodeStyleSettingsListener {
        recalculateInputs()
      },
      disposable,
    )
  }

  /**
   * An editor in a text editor also gets a file type change through `EditorHighlighterUpdater` and a new highlighter.
   * Other editors get it only here.
   */
  private fun subscribeToFileTypes(disposable: Disposable) {
    project?.messageBus?.connect(disposable)?.subscribe(
      FileTypeManager.TOPIC,
      object : FileTypeListener {
        override fun fileTypesChanged(event: FileTypeEvent) {
          recalculateInputs()
        }
      },
    )
  }

  private fun subscribeToEditorState(editor: EditorImpl, disposable: Disposable) {
    editor.state.addPropertyChangeListener(
      object : ObservableStateListener {
        override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
          when (event.propertyName) {
            EditorState::isColumnMode.name -> {
              refresh(::myIsVirtualSpace)
              refresh(::myIsCaretInsideTabs)
            }
            // A viewer takes its tab size from the file type, and it stores no options on the document.
            EditorState::isViewer.name, EditorState::isRendererMode.name -> {
              fileIndentInput.recalculate()
            }
          }
        }
      },
      disposable,
    )
  }

  /**
   * Refreshes the property that reads the [EditorSettingsExternalizable] setting with [propertyName].
   */
  private fun refreshFromEditorSettings(propertyName: String?) {
    when (propertyName) {
      EditorSettingsExternalizable.PropNames.PROP_IS_VIRTUAL_SPACE -> refresh(::myIsVirtualSpace)
      EditorSettingsExternalizable.PropNames.PROP_IS_CARET_INSIDE_TABS -> refresh(::myIsCaretInsideTabs)
      EditorSettingsExternalizable.PropNames.PROP_IS_CARET_BLINKING -> refresh(::myIsCaretBlinking)
      EditorSettingsExternalizable.PropNames.PROP_CARET_BLINKING_PERIOD -> refresh(::myCaretBlinkingPeriod)
      EditorSettingsExternalizable.PropNames.PROP_IS_SMOOTH_CARET_BLINKING -> refresh(::myIsSmoothCaretBlinking)
      EditorSettingsExternalizable.PropNames.PROP_IS_RIGHT_MARGIN_SHOWN -> refresh(::myIsRightMarginShown)
      EditorSettingsExternalizable.PropNames.PROP_IS_HIGHLIGHT_SELECTION_OCCURRENCES -> refresh(::myIsHighlightSelectionOccurrences)

      EditorSettingsExternalizable.PropNames.PROP_VERTICAL_SCROLL_OFFSET -> refresh(::myVerticalScrollOffset)
      EditorSettingsExternalizable.PropNames.PROP_VERTICAL_SCROLL_JUMP -> refresh(::myVerticalScrollJump)
      EditorSettingsExternalizable.PropNames.PROP_HORIZONTAL_SCROLL_OFFSET -> refresh(::myHorizontalScrollOffset)
      EditorSettingsExternalizable.PropNames.PROP_HORIZONTAL_SCROLL_JUMP -> refresh(::myHorizontalScrollJump)
      EditorSettingsExternalizable.PropNames.PROP_ARE_LINE_NUMBERS_SHOWN -> refresh(::myAreLineNumbersShown)
      EditorSettingsExternalizable.PropNames.PROP_ARE_GUTTER_ICONS_SHOWN -> refresh(::myGutterIconsShown)
      EditorSettingsExternalizable.PropNames.PROP_IS_FOLDING_OUTLINE_SHOWN -> refresh(::myIsFoldingOutlineShown)
      EditorSettingsExternalizable.PropNames.PROP_SMART_HOME -> refresh(::myIsSmartHome)
      EditorSettingsExternalizable.PropNames.PROP_IS_BLOCK_CURSOR -> refresh(::myIsBlockCursor)
      EditorSettingsExternalizable.PropNames.PROP_IS_FULL_LINE_HEIGHT_CURSOR -> refresh(::myIsFullLineHeightCursor)
      EditorSettingsExternalizable.PropNames.PROP_IS_ANIMATED_CARET -> refresh(::myIsSmoothCaretMovement)
      EditorSettingsExternalizable.PropNames.PROP_CARET_EASING -> refresh(::myCaretEasing)
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
      EditorSettingsExternalizable.PropNames.PROP_IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS -> refresh(::myIsMouseClickSelectionHonorsCamelWords)
      EditorSettingsExternalizable.PropNames.PROP_RENAME_VARIABLES_INPLACE -> refresh(::myIsRenameVariablesInplace)
      EditorSettingsExternalizable.PropNames.PROP_REFRAIN_FROM_SCROLLING -> refresh(::myIsRefrainFromScrolling)
      EditorSettingsExternalizable.PropNames.PROP_USE_SOFT_WRAPS,
      EditorSettingsExternalizable.PropNames.PROP_SOFT_WRAP_FILE_MASKS -> refresh(::myUseSoftWraps)
      EditorSettingsExternalizable.PropNames.PROP_USE_CUSTOM_SOFT_WRAP_INDENT -> refresh(::myUseCustomSoftWrapIndent)
      EditorSettingsExternalizable.PropNames.PROP_CUSTOM_SOFT_WRAP_INDENT -> refresh(::myCustomSoftWrapIndent)
      EditorSettingsExternalizable.PropNames.PROP_PRESELECT_RENAME -> refresh(::myRenamePreselect)
      EditorSettingsExternalizable.PropNames.PROP_SHOW_INTENTION_BULB -> refresh(::myShowIntentionBulb)
      EditorSettingsExternalizable.PropNames.PROP_IS_CAMEL_WORDS -> refresh(::myIsCamelWords)
      EditorSettingsExternalizable.PropNames.PROP_LINE_NUMERATION -> refresh(::myLineNumeration)
      EditorSettingsExternalizable.PropNames.PROP_SHOW_STICKY_LINES -> refresh(::myStickyLinesShown)
      EditorSettingsExternalizable.PropNames.PROP_SHOW_STICKY_LINES_PER_LANGUAGE -> refresh(::myStickyLinesShownForLanguage)
      EditorSettingsExternalizable.PropNames.PROP_STICKY_LINES_LIMIT -> refresh(::myStickyLinesLimit)

      EditorSettingsExternalizable.PropNames.PROP_IS_FOLDING_OUTLINE_SHOWN_ONLY_ON_HOVER -> refresh(::foldingOutlineShownOnlyOnHover)
      EditorSettingsExternalizable.PropNames.PROP_IS_FOLDING_ENDINGS_SHOWN -> refresh(::foldingEndingsShown)
      EditorSettingsExternalizable.PropNames.PROP_IS_WHEEL_FONTCHANGE_PERSISTENT -> refresh(::wheelFontChangePersistent)
      EditorSettingsExternalizable.PropNames.PROP_ADD_CARETS_ON_DOUBLE_CTRL -> refresh(::addCaretsOnDoubleCtrl)
      EditorSettingsExternalizable.PropNames.PROP_IS_ALL_SOFTWRAPS_SHOWN -> refresh(::allSoftWrapsShown)
      EditorSettingsExternalizable.PropNames.PROP_INSERT_PARENTHESES_AUTOMATICALLY -> refresh(::insertParenthesesAutomatically)
      EditorSettingsExternalizable.PropNames.PROP_SHOW_INSPECTION_WIDGET -> refresh(::showInspectionWidget)
      EditorSettingsExternalizable.PropNames.PROP_USE_EDITOR_FONT_IN_INLAYS -> refresh(::useEditorFontInInlays)
      EditorSettingsExternalizable.PropNames.PROP_BIDI_TEXT_DIRECTION -> refresh(::bidiTextDirection)
      EditorSettingsExternalizable.PropNames.PROP_CARET_STOP_OPTIONS -> refresh(::caretStopOptions)
    }
  }

  private fun updateLanguage(newLanguage: Language?) {
    if (language != newLanguage) {
      language = newLanguage
      refresh(::myStickyLinesShownForLanguage)
      marginsInput.recalculate()
    }
  }

  private fun updateMargins(newMargins: Margins) {
    margins = newMargins
    refresh(::myWrapWhenTypingReachesRightMargin)
    refresh(::softMargins)
    refresh(::rightMargin)
  }

  /**
   * The margins before the first computation.
   *
   * When the inputs compute in the background, the project settings give them, because those settings need no lock.
   * Otherwise, as in unit-test mode, the margins are computed at once.
   */
  private fun initialMargins(): Margins {
    if (BackgroundStateInput.computesInBackground()) {
      return marginsOf(CodeStyle.getProjectOrDefaultSettings(project))
    }
    return computeMargins()
  }

  private fun computeMargins(): Margins {
    return marginsOf(editorCodeStyleSettings())
  }

  private fun marginsOf(settings: CodeStyleSettings): Margins {
    return Margins(
      wrapOnTyping = settings.isWrapOnTyping(language),
      softMargins = settings.getSoftMargins(language),
      rightMargin = settings.getRightMargin(language),
    )
  }

  /**
   * The code style settings of the editor file. They are the settings that the editor holds, or else the settings of the
   * file, or else the project settings.
   */
  private fun editorCodeStyleSettings(): CodeStyleSettings {
    if (editor == null || project == null) {
      return CodeStyle.getProjectOrDefaultSettings(project)
    }
    val heldSettings = editor.getUserData(CODE_STYLE_SETTINGS)
    if (heldSettings != null) {
      return heldSettings
    }
    val file = editorFile(editor)
    if (file == null) {
      return CodeStyle.getSettings(project)
    }
    return CodeStyle.getSettings(project, file)
  }

  private fun editorFile(editor: EditorImpl): VirtualFile? {
    return editor.virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document)
  }

  /**
   * Stores [newFileIndent], and refreshes the two properties that read it. An editor that is not a viewer also stores
   * the per-file options on its document.
   */
  private fun updateFileIndent(newFileIndent: FileIndent) {
    val fileOptions = newFileIndent.fileOptions
    if (editor != null && !editor.isViewer && fileOptions != null) {
      fileOptions.associateWithDocument(editor.document)
    }
    fileIndent = newFileIndent
    // The tab size event refreshes the editor, so the tab character must have its new value before that event.
    refresh(::myUseTabCharacter)
    refresh(::tabSize)
  }

  /**
   * Computes the value for a getter in unit-test mode.
   *
   * The options stored on the document answer first, because a call to the providers can have side effects. For example,
   * `DetectableIndentOptionsProvider` stores new options after an edit, and it runs the detection at once in unit-test
   * mode.
   */
  private fun computeFileIndentForTest(): FileIndent {
    val result = runCatching {
      computeFileIndent(ignoreDocOptions = false)
    }.getOrLogException(LOG)
    return result ?: projectFileIndent()
  }

  /**
   * Computes the [FileIndent] of the editor file.
   *
   * The per-file options need the PSI, so the caller must have read access. [ignoreDocOptions] skips the options stored
   * on the document. A recompute sets it, because its result replaces those options.
   */
  private fun computeFileIndent(ignoreDocOptions: Boolean): FileIndent {
    if (editor == null || project == null) {
      return projectFileIndent()
    }
    val file = editorFile(editor)
    if (file == null) {
      return projectFileIndent()
    }
    val settings = editor.getUserData(CODE_STYLE_SETTINGS) ?: CodeStyle.getSettings(project, file)
    val fileOptions = ProjectLocator.withPreferredProject(file, project).use {
      settings.getIndentOptionsByFile(project, file, null, ignoreDocOptions, null)
    }
    // A viewer takes its tab size from the file type.
    val tabSize = if (editor.isViewer) {
      CodeStyle.getSettings(project).getIndentOptions(file.fileType).TAB_SIZE
    } else {
      fileOptions.TAB_SIZE
    }
    return FileIndent(
      tabSize = max(1, tabSize),
      useTabCharacter = fileOptions.USE_TAB_CHARACTER,
      fileOptions = fileOptions,
    )
  }

  /**
   * The [FileIndent] that the project setting gives. It needs no lock.
   */
  private fun projectFileIndent(): FileIndent {
    val options = CodeStyle.getProjectOrDefaultSettings(project).indentOptions
    return FileIndent(
      tabSize = max(1, options.TAB_SIZE),
      useTabCharacter = options.USE_TAB_CHARACTER,
      fileOptions = null,
    )
  }

  /**
   * A main editor uses soft wraps when the setting is on and the file name matches a soft wrap mask.
   */
  private fun computeUseSoftWraps(): Boolean {
    val softWrapsEnabled = externalizable().isUseSoftWraps(softWrapAppliancePlace)
    if (!softWrapsEnabled || softWrapAppliancePlace != SoftWrapAppliancePlaces.MAIN_EDITOR || editor == null) {
      return softWrapsEnabled
    }
    val masks = externalizable().softWrapFileMasks
    if (masks.trim() == "*") {
      return true
    }
    val file = FileDocumentManager.getInstance().getFile(editor.document)
    return file != null && fileNameMatches(file.name, masks)
  }

  private fun fileNameMatches(fileName: String, globPatterns: String): Boolean {
    return globPatterns.splitToSequence(';')
      .map { it.trim() }
      .filter { !it.isEmpty() }
      .any { PatternUtil.fromMask(it).matcher(fileName).matches() }
  }

  /**
   * The sticky lines setting for the language of the editor file.
   *
   * Without a language, the value is `true`. That avoids a late appearance of the sticky panel. If the setting for the
   * language turns out to be `false`, the editor removes all lines from the sticky model later.
   */
  private fun computeStickyLinesShownForLanguage(): Boolean {
    val fileLanguage = language ?: return true
    val supportedLanguage = project?.service<StickyLinesLanguageSupport>()?.supportedLang(fileLanguage) ?: fileLanguage
    return externalizable().areStickyLinesShownFor(supportedLanguage.id)
  }

  /**
   * Makes the value `true` while the editor is in column mode.
   */
  private fun trueInColumnMode(): CustomOutValueModifier<Boolean> {
    return CustomOutValueModifier { editor != null && editor.isColumnMode || it }
  }

  /**
   * Makes the value follow the global setting when the true smooth scrolling is on, because that scrolling uses its own
   * interpolation.
   */
  private fun smoothScrollingWithOwnInterpolation(): CustomOutValueModifier<Boolean> {
    return CustomOutValueModifier {
      if (EditorCoreUtil.isTrueSmoothScrollingEnabled()) {
        externalizable().isSmoothScrolling
      } else {
        it
      }
    }
  }

  private fun externalizable(): EditorSettingsExternalizable {
    return EditorSettingsExternalizable.getInstance()
  }

  /**
   * A [property] whose value [outValueModifier] changes. The change applies to the override and to the default.
   */
  private inline fun <reified T> modifiedProperty(
    outValueModifier: CustomOutValueModifier<T>,
    noinline defaultValueCalculator: () -> T,
  ): StateProperty<T> {
    return property(
      initialValue = defaultValueCalculator(),
      defaultValueCalculator = SyncDefaultValueCalculator(defaultValueCalculator),
      outValueModifier = outValueModifier,
    )
  }

  /**
   * A property whose default comes from the code style, through an input. [initialValue] is the value before the input is
   * computed, because the calculator reads an input field that is not set while the state is built.
   *
   * The default can depend on data that only the Remote Development backend has, such as `.editorconfig` and
   * `.clang-format`. So the backend always sends the value, also without an override.
   */
  private inline fun <reified T> codeStyleProperty(
    initialValue: T,
    noinline defaultValueCalculator: () -> T,
  ): StateProperty<T> {
    return property(
      initialValue = initialValue,
      defaultValueCalculator = SyncDefaultValueCalculator(defaultValueCalculator),
      alwaysTransfer = true,
    )
  }

  /**
   * The margins that the code style gives the editor file in its language.
   */
  private class Margins(
    val wrapOnTyping: Boolean,
    val softMargins: List<Int>,
    val rightMargin: Int,
  )

  /**
   * The indent values that the code style gives the editor file.
   */
  private class FileIndent(
    val tabSize: Int, // In a viewer, this value comes from the file type, and not from [fileOptions].
    val useTabCharacter: Boolean,
    val fileOptions: IndentOptions?, // The per-file options, or `null` when the editor has no file or no project.
  )

  companion object {
    private val LOG: Logger = logger<EditorSettingsState>()

    /**
     * Makes the inputs of every state compute in the background, as in production, until [disposable] is disposed.
     */
    @TestOnly
    @JvmStatic
    fun computeInputsInBackgroundInTests(disposable: Disposable) {
      BackgroundStateInput.computeInBackgroundInTests(disposable)
    }
  }
}
