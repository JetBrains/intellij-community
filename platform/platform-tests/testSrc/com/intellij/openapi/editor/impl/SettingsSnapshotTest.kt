// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings.CaretEasing
import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.actions.CaretStopOptions
import com.intellij.openapi.editor.actions.CaretStopPolicy
import com.intellij.openapi.editor.ex.BidiTextDirection
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.editor.state.TransferableProperty
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.xmlb.XmlSerializerUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The getters of [SettingsImpl] read the [SettingsSnapshot] of its state, so each change of the state must publish a new
 * snapshot.
 */
@TestApplication
class SettingsSnapshotTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private const val CHANGES = 10_000
    private const val TIMEOUT_SECONDS = 30L
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  /**
   * The settings keep no copy of the values. Their snapshot is the snapshot of the state, also after a change.
   */
  @Test
  fun testTheSettingsSnapshotIsTheSnapshotOfTheState(): Unit = fixture.runTest {
    val settings = SettingsImpl()
    assertSame(settings.getState().getSnapshot(), settings.getSnapshot())

    settings.isLineNumbersShown = !settings.isLineNumbersShown

    assertSame(settings.getState().getSnapshot(), settings.getSnapshot())
  }

  /**
   * A wrong position in [SettingsSnapshot] gives a getter the value of another setting, so each setter must reach its own
   * getter.
   */
  @Test
  fun testEachSetterReachesItsGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val booleans: List<Accessors<Boolean>> = listOf(
      Accessors(settings::isLineNumbersShown, settings::setLineNumbersShown),
      Accessors(settings::isLineNumbersAfterIcons, settings::setLineNumbersAfterIcons),
      Accessors(settings::isLineMarkerAreaShown, settings::setLineMarkerAreaShown),
      Accessors(settings::areGutterIconsShown, settings::setGutterIconsShown),
      Accessors(settings::isFoldingOutlineShown, settings::setFoldingOutlineShown),
      Accessors(settings::isAutoCodeFoldingEnabled, settings::setAutoCodeFoldingEnabled),
      Accessors(settings::isAllowSingleLogicalLineFolding, settings::setAllowSingleLogicalLineFolding),
      Accessors(settings::isRightMarginShown, settings::setRightMarginShown),
      Accessors(settings::isHighlightSelectionOccurrences, settings::setHighlightSelectionOccurrences),
      Accessors(settings::isWhitespacesShown, settings::setWhitespacesShown),
      Accessors(settings::isLeadingWhitespaceShown, settings::setLeadingWhitespaceShown),
      Accessors(settings::isInnerWhitespaceShown, settings::setInnerWhitespaceShown),
      Accessors(settings::isTrailingWhitespaceShown, settings::setTrailingWhitespaceShown),
      Accessors(settings::isSelectionWhitespaceShown, settings::setSelectionWhitespaceShown),
      Accessors(settings::isIndentGuidesShown, settings::setIndentGuidesShown),
      Accessors(settings::isCaretRowShown, settings::setCaretRowShown),
      Accessors(settings::isShowIntentionBulb, settings::setShowIntentionBulb),
      Accessors(settings::isRtlEnabled, settings::setRtlEnabled),
      Accessors(settings::isShowingSpecialChars, settings::setShowingSpecialChars),
      Accessors(settings::isBlockCursor, settings::setBlockCursor),
      Accessors(settings::isFullLineHeightCursor, settings::setFullLineHeightCursor),
      Accessors(settings::isSmoothCaretMovement, settings::setSmoothCaretMovement),
      Accessors(settings::isBlinkCaret, settings::setBlinkCaret),
      Accessors(settings::isSmoothCaretBlinking, settings::setSmoothCaretBlinking),
      Accessors(settings::isVirtualSpace, settings::setVirtualSpace),
      Accessors(settings::isCaretInsideTabs, settings::setCaretInsideTabs),
      Accessors(settings::isSmartHome, settings::setSmartHome),
      Accessors(settings::isCamelWords, settings::setCamelWords),
      Accessors(settings::isMouseClickSelectionHonorsCamelWords, settings::setMouseClickSelectionHonorsCamelWords),
      Accessors(settings::isUseSoftWraps, settings::setUseSoftWraps),
      Accessors(settings::isPaintSoftWraps, settings::setPaintSoftWraps),
      Accessors(settings::isUseCustomSoftWrapIndent, settings::setUseCustomSoftWrapIndent),
      Accessors(settings::isAnimatedScrolling, settings::setAnimatedScrolling),
      Accessors(settings::isRefrainFromScrolling, settings::setRefrainFromScrolling),
      Accessors(settings::isAdditionalPageAtBottom, settings::setAdditionalPageAtBottom),
      Accessors(settings::isDndEnabled, settings::setDndEnabled),
      Accessors(settings::isWheelFontChangeEnabled, settings::setWheelFontChangeEnabled),
      Accessors(settings::isVariableInplaceRenameEnabled, settings::setVariableInplaceRenameEnabled),
      Accessors(settings::isPreselectRename, settings::setPreselectRename),
      Accessors({ settings.isWrapWhenTypingReachesRightMargin(project) }, settings::setWrapWhenTypingReachesRightMargin),
      Accessors({ settings.isUseTabCharacter(project) }, settings::setUseTabCharacter),
    )
    for (accessors in booleans) {
      assertSetterReachesGetter(accessors, !accessors.getter())
    }

    val ints: List<Accessors<Int>> = listOf(
      Accessors(settings::getAdditionalLinesCount, settings::setAdditionalLinesCount),
      Accessors(settings::getAdditionalColumnsCount, settings::setAdditionalColumnsCount),
      Accessors(settings::getLineCursorWidth, settings::setLineCursorWidth),
      Accessors(settings::getCaretBlinkPeriod, settings::setCaretBlinkPeriod),
      Accessors(settings::getCustomSoftWrapIndent, settings::setCustomSoftWrapIndent),
      Accessors(settings::getVerticalScrollOffset, settings::setVerticalScrollOffset),
      Accessors(settings::getVerticalScrollJump, settings::setVerticalScrollJump),
      Accessors(settings::getHorizontalScrollOffset, settings::setHorizontalScrollOffset),
      Accessors(settings::getHorizontalScrollJump, settings::setHorizontalScrollJump),
      Accessors({ settings.getRightMargin(project) }, settings::setRightMargin),
      Accessors({ settings.getTabSize(project) }, settings::setTabSize),
    )
    for (accessors in ints) {
      assertSetterReachesGetter(accessors, accessors.getter() + 3)
    }

    val lineNumeration = LineNumerationType.entries.first { it != settings.lineNumerationType }
    settings.lineNumerationType = lineNumeration
    assertEquals(lineNumeration, settings.lineNumerationType)

    settings.characterGridWidthMultiplier = 1.5f
    assertEquals(1.5f, settings.characterGridWidthMultiplier)

    settings.setSoftMargins(listOf(17, 42))
    assertEquals(listOf(17, 42), settings.softMargins)
  }

  /**
   * A global-only setting has no setter, so only the global setting can show that it reaches its getter.
   * `SettingsImplTest.testGlobalOnlySettingsFollowTheGlobalSetting` covers the other global-only settings.
   */
  @Test
  fun testEachGlobalOnlySettingReachesItsGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings
    val global = EditorSettingsExternalizable.getInstance()

    assertFollowsTheGlobalSetting(
      { global.areStickyLinesShown() },
      { global.setStickyLinesShown(it) },
      settings::areStickyLinesShown,
      !global.areStickyLinesShown(),
    )
    assertFollowsTheGlobalSetting(
      global::getStickyLineLimit,
      global::setStickyLineLimit,
      settings::getStickyLinesLimit,
      global.stickyLineLimit + 3,
    )
    assertFollowsTheGlobalSetting(
      global::getCaretEasing,
      global::setCaretEasing,
      settings::getCaretEasing,
      CaretEasing.entries.first { it != global.caretEasing },
    )
    assertFollowsTheGlobalSetting(
      global::getBidiTextDirection,
      global::setBidiTextDirection,
      settings::getBidiTextDirection,
      BidiTextDirection.entries.first { it != global.bidiTextDirection },
    )
    val caretStops = listOf(
      CaretStopOptions(CaretStopPolicy.BOTH, CaretStopPolicy.NONE),
      CaretStopOptions(CaretStopPolicy.NONE, CaretStopPolicy.BOTH),
    )
    assertFollowsTheGlobalSetting(
      global::getCaretStopOptions,
      global::setCaretStopOptions,
      settings::getCaretStopOptions,
      caretStops.first { it != global.caretStopOptions },
    )
  }

  /**
   * The sticky lines need the global setting and the setting for the language of the editor file.
   */
  @Test
  fun testTheStickyLinesOfTheLanguageReachTheGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings
    val global = EditorSettingsExternalizable.getInstance()
    val originalShown = global.areStickyLinesShown()
    settings.setLanguageSupplier { Language.ANY }
    try {
      global.setStickyLinesShown(true)
      assertTrue(settings.areStickyLinesShown())
      global.setStickyLinesShownFor(Language.ANY.id, false)
      assertFalse(settings.areStickyLinesShown())
    } finally {
      global.setStickyLinesShownFor(Language.ANY.id, true)
      global.setStickyLinesShown(originalShown)
    }
  }

  /**
   * An override with the current value changes no value, so only the override event can publish the override flag.
   * Settings without an editor have no project, so without the flag a caller with a project gets the code style value.
   * The two indent values get different code style values, so a swap of the two flags fails too.
   */
  @Test
  fun testAnOverrideWithTheCurrentValueReachesTheGetter(): Unit = fixture.runTest {
    val settings = SettingsImpl()
    val tabSize = settings.getTabSize(null)
    val useTabCharacter = settings.isUseTabCharacter(null)
    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.TAB_SIZE = tabSize + 3
    temporary.indentOptions.USE_TAB_CHARACTER = !useTabCharacter

    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      settings.setTabSize(tabSize)
      assertEquals(tabSize, settings.getTabSize(project))
      assertEquals(!useTabCharacter, settings.isUseTabCharacter(project))

      settings.setUseTabCharacter(useTabCharacter)
      assertEquals(useTabCharacter, settings.isUseTabCharacter(project))
    })
  }

  /**
   * Two threads change two settings at the same time, and each thread ends with its own last value. The changes of the
   * state run one after another, so the last snapshot has both last values. Settings without an editor refresh no
   * editor, so a change off the EDT is safe here.
   */
  @Test
  fun testConcurrentChangesEndInTheLastValues(): Unit = fixture.runTest {
    val settings = SettingsImpl()
    val start = CountDownLatch(1)
    val application = ApplicationManager.getApplication()
    val lineNumbers = application.executeOnPooledThread {
      start.await()
      repeat(CHANGES) { settings.isLineNumbersShown = it % 2 == 0 }
      settings.isLineNumbersShown = true
    }
    val caretRow = application.executeOnPooledThread {
      start.await()
      repeat(CHANGES) { settings.isCaretRowShown = it % 2 == 0 }
      settings.isCaretRowShown = false
    }
    start.countDown()
    lineNumbers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    caretRow.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    assertTrue(settings.isLineNumbersShown)
    assertFalse(settings.isCaretRowShown)
  }

  /**
   * In unit-test mode, the inputs compute at once.
   */
  @Test
  fun testEachInputResultPublishesOneSnapshot(): Unit = fixture.runTest {
    assertEachInputResultPublishesOneSnapshot()
  }

  /**
   * In production, each input computes in the background, and its result comes in a later EDT event.
   */
  @Test
  fun testEachBackgroundInputResultPublishesOneSnapshot(): Unit = fixture.runTest {
    EditorSettingsState.computeInputsInBackgroundInTests(testRootDisposable)
    assertEachInputResultPublishesOneSnapshot()
  }

  /**
   * Column mode shows the virtual space and the caret inside tabs, so a change of the mode changes both in one snapshot.
   */
  @Test
  fun testAColumnModeChangePublishesOneSnapshot(): Unit = fixture.runTest {
    initText("abc")
    val editorImpl = editor as EditorImpl
    val settings = editorImpl.settings as SettingsImpl
    val state = settings.getState()
    settings.isVirtualSpace = false
    settings.isCaretInsideTabs = false
    val snapshots = recordSnapshots(settings, testRootDisposable)
    try {
      editorImpl.isColumnMode = true
      val published = requireNotNull(snapshots[state::myIsVirtualSpace.name])
      assertSame(published, snapshots[state::myIsCaretInsideTabs.name])
      assertTrue(published.isVirtualSpace())
      assertTrue(published.isCaretInsideTabs())
    } finally {
      editorImpl.isColumnMode = false
    }
  }

  /**
   * A reload of the stored options refreshes all the properties in one snapshot.
   */
  @Test
  fun testAReloadPublishesOneSnapshot(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val state = settings.getState()
    val snapshots = recordSnapshots(settings, testRootDisposable)
    val global = EditorSettingsExternalizable.getInstance()
    val original = XmlSerializerUtil.createCopy(global.state)
    val reloaded = XmlSerializerUtil.createCopy(global.state)
    reloaded.ARE_LINE_NUMBERS_SHOWN = !original.ARE_LINE_NUMBERS_SHOWN
    reloaded.IS_FOLDING_ENDINGS_SHOWN = !original.IS_FOLDING_ENDINGS_SHOWN
    try {
      loadOnAPooledThread(global, reloaded)
      val published = requireNotNull(snapshots[state::myAreLineNumbersShown.name])
      assertSame(published, snapshots[state::foldingEndingsShown.name])
      assertEquals(reloaded.ARE_LINE_NUMBERS_SHOWN, published.isLineNumbersShown())
      assertEquals(reloaded.IS_FOLDING_ENDINGS_SHOWN, published.isFoldingEndingsShown())
    } finally {
      loadOnAPooledThread(global, original)
    }
  }

  /**
   * A reset changes the value back to the global setting, so it must publish a new snapshot too.
   */
  @Test
  fun testAResetReachesTheGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings
    val global = EditorSettingsExternalizable.getInstance().isCamelWords

    settings.isCamelWords = !global
    settings.resetCamelWords()
    assertEquals(global, settings.isCamelWords)
  }

  /**
   * The quiet switch refreshes no editor, and it must still publish a new snapshot.
   */
  @Test
  fun testAQuietSoftWrapChangeReachesTheGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    settings.isUseSoftWraps = false

    settings.setUseSoftWrapsQuiet()
    assertTrue(settings.isUseSoftWraps)
  }

  /**
   * The Remote Development frontend writes a value of the backend to the state directly, as
   * `FrontendEditorSettingsUtils.applyValue` does.
   */
  @Test
  fun testAValueThatTheFrontendAppliesReachesTheGetter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val state = settings.getState()
    val property = requireNotNull(state.findProperty(state::tabSize.name))
    val transferable = property as TransferableProperty<*>
    val backendValue = settings.getTabSize(project) + 5

    property.setValue(state, requireNotNull(transferable.decodeFromString(backendValue.toString())))
    assertEquals(backendValue, settings.getTabSize(project))
  }

  /**
   * A snapshot is immutable, so a later change of the list of a caller must not reach it.
   */
  @Test
  fun testASnapshotKeepsItsSoftMargins(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val margins = mutableListOf(17, 42)

    settings.setSoftMargins(margins)
    val before = settings.getSnapshot()
    margins.add(80)
    assertEquals(listOf(17, 42), before.getSoftMargins())
    assertEquals(listOf(17, 42), settings.softMargins)
  }

  /**
   * The Remote Development frontend decodes the soft margins into a mutable list. A later change of that list must not
   * reach a snapshot.
   */
  @Test
  fun testASnapshotKeepsTheSoftMarginsFromTheFrontend(): Unit = fixture.runTest {
    val settings = SettingsImpl()
    val state = settings.getState()
    val property = requireNotNull(state.findProperty(state::softMargins.name))
    val decoded = requireNotNull((property as TransferableProperty<*>).decodeFromString("[17,42]"))

    property.setValue(state, decoded)
    // Safe: the kotlinx decoder gives an ArrayList of the integers.
    @Suppress("UNCHECKED_CAST")
    (decoded as MutableList<Int>).add(80)

    assertEquals(listOf(17, 42), settings.getSnapshot().getSoftMargins())
  }

  /**
   * Each change publishes a new snapshot, and an older snapshot keeps its values.
   */
  @Test
  fun testAChangePublishesANewSnapshot(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val before = settings.getSnapshot()
    val original = before.isLineNumbersShown()

    settings.isLineNumbersShown = !original
    val after = settings.getSnapshot()
    assertNotSame(before, after)
    assertEquals(original, before.isLineNumbersShown())
    assertEquals(!original, after.isLineNumbersShown())
  }

  /**
   * Changes the indent values and the margins of the code style. The result of each input changes two properties, and
   * both events must see one snapshot with both new values.
   */
  private fun EditorModelFixture.assertEachInputResultPublishesOneSnapshot() {
    val editorFactory = EditorFactory.getInstance()
    val created = editorFactory.createEditor(editorFactory.createDocument("abc"), project)
    try {
      val settings = created.settings as SettingsImpl
      val state = settings.getState()
      val snapshots = recordSnapshots(settings, testRootDisposable)
      val tabSize = state.tabSize + 3
      val useTabCharacter = !state.myUseTabCharacter
      val rightMargin = state.rightMargin + 7
      val wrapOnTyping = !state.myWrapWhenTypingReachesRightMargin
      val manager = CodeStyleSettingsManager.getInstance(project)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
      temporary.indentOptions.TAB_SIZE = tabSize
      temporary.indentOptions.USE_TAB_CHARACTER = useTabCharacter
      temporary.defaultRightMargin = rightMargin
      temporary.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = wrapOnTyping

      CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
        manager.fireCodeStyleSettingsChanged()
        PlatformTestUtil.waitWithEventsDispatching(
          "The input results did not reach the state",
          { state.tabSize == tabSize && state.rightMargin == rightMargin },
          TIMEOUT_SECONDS.toInt(),
        )
      })

      val indentSnapshot = requireNotNull(snapshots[state::tabSize.name])
      assertSame(indentSnapshot, snapshots[state::myUseTabCharacter.name])
      assertEquals(tabSize, indentSnapshot.getTabSize())
      assertEquals(useTabCharacter, indentSnapshot.isUseTabCharacter())
      val marginsSnapshot = requireNotNull(snapshots[state::rightMargin.name])
      assertSame(marginsSnapshot, snapshots[state::myWrapWhenTypingReachesRightMargin.name])
      assertEquals(rightMargin, marginsSnapshot.getRightMargin())
      assertEquals(wrapOnTyping, marginsSnapshot.isWrapWhenTypingReachesRightMargin())
    }
    finally {
      editorFactory.releaseEditor(created)
    }
  }

  /**
   * Records the snapshot that each value event of [settings] sees, by the property name. The state publishes its
   * snapshot before it gives the events.
   */
  private fun recordSnapshots(settings: SettingsImpl, disposable: Disposable): Map<String, SettingsSnapshot> {
    val snapshots = HashMap<String, SettingsSnapshot>()
    settings.getState().addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        snapshots[event.propertyName] = settings.getSnapshot()
      }
    }, disposable)
    return snapshots
  }

  /**
   * Loads [options] on a pooled thread, as a reload of the stored settings can do, and then runs the events on the EDT.
   */
  private fun loadOnAPooledThread(global: EditorSettingsExternalizable, options: EditorSettingsExternalizable.OptionSet) {
    ApplicationManager.getApplication().executeOnPooledThread {
      global.loadState(options)
    }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  /**
   * Calls the setter of [accessors] with [changed], and checks that the getter returns it.
   */
  private fun <T> assertSetterReachesGetter(accessors: Accessors<T>, changed: T) {
    accessors.setter(changed)
    assertEquals(changed, accessors.getter())
  }

  /**
   * Sets the global setting to [changed], checks that [editorValue] follows it, and then restores the global setting.
   */
  private fun <T> assertFollowsTheGlobalSetting(
    globalValue: () -> T,
    setGlobalValue: (T) -> Unit,
    editorValue: () -> T,
    changed: T,
  ) {
    val original = globalValue()
    try {
      setGlobalValue(changed)
      assertEquals(changed, editorValue())
    } finally {
      setGlobalValue(original)
    }
    assertEquals(original, editorValue())
  }
}

/**
 * The getter and the setter of one setting.
 */
private class Accessors<T>(val getter: () -> T, val setter: (T) -> Unit)
