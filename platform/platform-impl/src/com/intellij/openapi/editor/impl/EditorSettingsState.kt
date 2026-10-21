// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.EditorSettings
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
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentListener
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
class EditorSettingsState(
  private val editor: EditorImpl?,
  internal val project: Project?,
  private val softWrapAppliancePlace: SoftWrapAppliancePlaces,
) : ObservableState() {

  // This group of settings does not have a UI
  var myAdditionalLinesCount: Int by property(Registry.intValue("editor.virtual.lines", 5))
  var myAdditionalColumnsCount: Int by property(3)
  var myLineCursorWidth: Int by property(EditorUtil.getDefaultCaretWidth())
  var myLineMarkerAreaShown: Boolean by property(true)
  var myAllowSingleLogicalLineFolding: Boolean by property(false)
  var myAutoCodeFoldingEnabled: Boolean by property(true)
  var myAreLineNumbersAfterIcons: Boolean by property { false }

  /**
   * The indent values that the code style gives the editor file. [myUseTabCharacter] and [tabSize] calculate their
   * defaults from it.
   *
   * The per-file value needs the PSI, and therefore the RW lock, so a calculator must not compute it. An editor can be
   * built on `Dispatchers.UI`, which forbids that lock (IJPL-243574). [recalculateFileIndent] computes the value in a
   * background read action, and then refreshes the two properties.
   *
   * The seed is the project setting, and not the global one. It is what the Remote Development frontend reads until the
   * backend pushes the computed value, and this value selects the character an indent writes. The project setting needs
   * no lock, and it is right for every file that no `FileIndentOptionsProvider` overrides.
   *
   * The field comes before the two properties, because their calculators read it while the state is constructed.
   */
  private var fileIndent: FileIndent = projectFileIndent()

  // These come from CodeStyleSettings.

  /**
   * Read this value through [currentUseTabCharacter]. That function starts the computation of [fileIndent], and a
   * direct read does not.
   */
  var myUseTabCharacter: Boolean by codeStyleProperty { fileIndent.useTabCharacter }

  /**
   * Read this value through [currentTabSize]. That function starts the computation of [fileIndent], and a direct read
   * does not.
   */
  var tabSize: Int by codeStyleProperty { fileIndent.tabSize }

  var myWrapWhenTypingReachesRightMargin: Boolean by codeStyleProperty {
    val settings = if (editor == null) {
      CodeStyle.getDefaultSettings()
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor)
    }
    settings.isWrapOnTyping(language)
  }
  var softMargins: List<Int> by codeStyleProperty {
    if (editor == null) {
      mutableListOf()
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor).getSoftMargins(language)
    }
  }
  var rightMargin: Int by codeStyleProperty {
    val settings = if (editor == null) {
      CodeStyle.getProjectOrDefaultSettings(project)
    }
    else {
      getEditorCodeStyleSettingsOrDefaults(editor)
    }
    settings.getRightMargin(language)
  }

  // These come from EditorSettingsExternalizable defaults.
  var myIsVirtualSpace: Boolean by property(
    initialValue = EditorSettingsExternalizable.getInstance().isVirtualSpace,
    defaultValueCalculator = SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isVirtualSpace },
    outValueModifier = CustomOutValueModifier { editor != null && editor.isColumnMode || it },
  )
  var myIsCaretInsideTabs: Boolean by property(
    initialValue = EditorSettingsExternalizable.getInstance().isCaretInsideTabs,
    defaultValueCalculator = SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isCaretInsideTabs },
    outValueModifier = CustomOutValueModifier { editor != null && editor.isColumnMode || it },
  )
  var myIsCaretBlinking: Boolean by property { EditorSettingsExternalizable.getInstance().isBlinkCaret }
  var myCaretBlinkingPeriod: Int by property { EditorSettingsExternalizable.getInstance().blinkPeriod }
  var myIsSmoothCaretBlinking: Boolean by property { EditorSettingsExternalizable.getInstance().isSmoothBlinkCaret }
  var myIsRightMarginShown: Boolean by property(
    initialValue = EditorSettingsExternalizable.getInstance().isRightMarginShown
  ) {
    if (editor != null && rightMargin == CodeStyleConstraints.MAX_RIGHT_MARGIN) {
      false
    } else {
      EditorSettingsExternalizable.getInstance().isRightMarginShown
    }
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
  var myIsSmoothCaretMovement: Boolean by property { EditorSettingsExternalizable.getInstance().isSmoothCaretMovement }
  var myCaretEasing: EditorSettings.CaretEasing by property { EditorSettingsExternalizable.getInstance().caretEasing }
  var myCaretRowShown: Boolean by property { EditorSettingsExternalizable.getInstance().isCaretRowShown }
  var myIsWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isWhitespacesShown }
  var myIsLeadingWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isLeadingWhitespacesShown }
  var myIsInnerWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isInnerWhitespacesShown }
  var myIsTrailingWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isTrailingWhitespacesShown }
  var myIsSelectionWhitespacesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isSelectionWhitespacesShown }
  var myIndentGuidesShown: Boolean by property { EditorSettingsExternalizable.getInstance().isIndentGuidesShown }
  var myIsAnimatedScrolling: Boolean by property(
    initialValue = EditorSettingsExternalizable.getInstance().isSmoothScrolling,
    defaultValueCalculator = SyncDefaultValueCalculator { EditorSettingsExternalizable.getInstance().isSmoothScrolling },
    outValueModifier = CustomOutValueModifier {
      if (EditorCoreUtil.isTrueSmoothScrollingEnabled()) { // [P.Fatin] uses its own interpolation
        EditorSettingsExternalizable.getInstance().isSmoothScrolling
      } else {
        it
      }
    },
  )
  var myIsAdditionalPageAtBottom: Boolean by property { EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom }
  var myIsDndEnabled: Boolean by property { EditorSettingsExternalizable.getInstance().isDndEnabled }
  var myIsWheelFontChangeEnabled: Boolean by property { EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled }
  var myIsMouseClickSelectionHonorsCamelWords: Boolean by property { EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords }
  var myIsRenameVariablesInplace: Boolean by property { EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled }
  var myIsRefrainFromScrolling: Boolean by property { EditorSettingsExternalizable.getInstance().isRefrainFromScrolling }
  var myUseSoftWraps: Boolean by property {
    val softWrapsEnabled = EditorSettingsExternalizable.getInstance().isUseSoftWraps(softWrapAppliancePlace)
    if (!softWrapsEnabled || softWrapAppliancePlace != SoftWrapAppliancePlaces.MAIN_EDITOR || editor == null) {
      return@property softWrapsEnabled
    }
    val masks = EditorSettingsExternalizable.getInstance().softWrapFileMasks
    if (masks.trim() == "*") {
      return@property true
    }
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
      languageInput.recalculate()
    }
  private var language: Language? = null
  private val languageInput: BackgroundStateInput<Language?> = BackgroundStateInput(
    scope = EditorCoroutineScopes.settingsScope(editor, project),
    compute = { languageSupplier?.invoke() },
    update = ::updateLanguage,
  )
  private val fileIndentInput: BackgroundStateInput<FileIndent> = BackgroundStateInput(
    scope = EditorCoroutineScopes.settingsScope(editor, project),
    compute = { computeFileIndent(ignoreDocOptions = true) },
    update = ::updateFileIndent,
  )

  init {
    if (editor != null) {
      @Suppress("DEPRECATION")
      val disposable = editor.disposable
      CodeStyleSettingsManager.getInstance(project).subscribe(
        CodeStyleSettingsListener {
          if (it.project != project || it.virtualFile != null && it.virtualFile != editor.virtualFile) {
            return@CodeStyleSettingsListener
          }
          refresh(::myWrapWhenTypingReachesRightMargin)
          refresh(::softMargins)
          refresh(::rightMargin)
        },
        disposable,
      )
      EditorSettingsExternalizable.getInstance().addPropertyChangeListener(
        PropertyChangeListener { propertyChangeEvent: PropertyChangeEvent? ->
          if (propertyChangeEvent == null) {
            return@PropertyChangeListener
          }
          when (propertyChangeEvent.propertyName) {
            EditorSettingsExternalizable.PropNames.PROP_IS_VIRTUAL_SPACE -> refresh(::myIsVirtualSpace)
            EditorSettingsExternalizable.PropNames.PROP_IS_CARET_INSIDE_TABS -> refresh(::myIsCaretInsideTabs)
            EditorSettingsExternalizable.PropNames.PROP_IS_CARET_BLINKING -> refresh(::myIsCaretBlinking)
            EditorSettingsExternalizable.PropNames.PROP_CARET_BLINKING_PERIOD -> refresh(::myCaretBlinkingPeriod)
            EditorSettingsExternalizable.PropNames.PROP_IS_SMOOTH_CARET_BLINKING -> refresh(::myIsSmoothCaretBlinking)
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
        },
        disposable,
      )
      val connection = project?.messageBus?.connect(disposable)
      connection?.subscribe(
        PsiDocumentListener.TOPIC,
        PsiDocumentListener { document, _, _ ->
          if (document == editor.document) {
            languageInput.recalculate()
          }
        },
      )
      connection?.subscribe(
        AdvancedSettingsChangeListener.TOPIC,
        object : AdvancedSettingsChangeListener {
          override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
            if (id == EDITOR_SHOW_SPECIAL_CHARS) {
              refresh(::showingSpecialCharacters)
            }
          }
        },
      )
      // An editor in a text editor also gets this change through `EditorHighlighterUpdater` and a new highlighter.
      // Other editors get it only here.
      connection?.subscribe(
        FileTypeManager.TOPIC,
        object : FileTypeListener {
          override fun fileTypesChanged(event: FileTypeEvent) {
            fileIndentInput.recalculate()
          }
        },
      )
      (editor.state as? ObservableState)?.addPropertyChangeListener(
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
  }

  private fun updateLanguage(newLanguage: Language?) {
    if (language != newLanguage) {
      language = newLanguage
      onLanguageChanged()
    }
  }

  private fun onLanguageChanged() {
    refresh(::softMargins)
    refresh(::rightMargin)
    refresh(::myStickyLinesShownForLanguage)
    refresh(::myWrapWhenTypingReachesRightMargin)
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
   * Computes [fileIndent] again, and then refreshes [tabSize] and [myUseTabCharacter].
   *
   * [SettingsImpl] calls this when it is built, and [SettingsImpl.reinitSettings] calls this after each event that can
   * change the value. These events are a code style change, a new file, and a new highlighter. `EditorHighlighterUpdater`
   * sets a new highlighter when the file type changes or the file gets a new name.
   */
  internal fun recalculateFileIndent() {
    fileIndentInput.recalculate()
  }

  /**
   * Waits until the background computations of the inputs end.
   */
  @TestOnly
  internal suspend fun awaitInputsForTests() {
    languageInput.awaitForTests()
    fileIndentInput.awaitForTests()
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
   * Computes the [FileIndent] of the editor file.
   *
   * The per-file options need the PSI, so the caller must have read access. [ignoreDocOptions] skips the options stored
   * on the document. A recompute sets it, because its result replaces those options.
   */
  private fun computeFileIndent(ignoreDocOptions: Boolean): FileIndent {
    if (editor == null || project == null) {
      return projectFileIndent()
    }
    val file = editor.virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document)
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
    } else {
      CodeStyle.getDefaultSettings()
    }
  }

  // Use for code-style-derived properties: their defaults depend on backend-side data (e.g. .clang-format, .editorconfig)
  // that the frontend in Remote Development cannot compute independently, so they must always be transferred.
  private inline fun <reified T> codeStyleProperty(
    noinline defaultValueCalculator: () -> T,
  ): StateProperty<T> {
    return property(
      initialValue = defaultValueCalculator(),
      defaultValueCalculator = SyncDefaultValueCalculator(defaultValueCalculator),
      alwaysTransfer = true,
    )
  }

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
  }
}
