// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.editor.EditorFactory
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val EDITOR_IMPL_PACKAGE = "com.intellij.openapi.editor.impl"

/** The prefixes of the lock machinery itself, which every reported stack trace starts with. */
private val LOCK_MACHINERY_PACKAGES = listOf(
  "com.intellij.platform.locking.",
  "com.intellij.openapi.application.",
  "com.intellij.util.concurrency.",
)

/**
 * The class that asked for the lock. It is the first frame below the machinery that reports the request.
 */
private fun Throwable.acquisitionSite(): String? =
  stackTrace.firstOrNull { frame -> LOCK_MACHINERY_PACKAGES.none { frame.className.startsWith(it) } }?.className

/**
 * An editor can be built on `Dispatchers.UI`, which forbids the RW lock. So the platform must take no lock
 * while the editor is built. See IJPL-243574. `EditorSettingsState` computes the tab size and the tab character
 * in a background read action instead, so these tests also cover where these values come from.
 */
@TestApplication
class EditorSettingsLockFreeCreationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  /**
   * `EditorTextField` builds its editor from `addNotify`, and that can run on `Dispatchers.UI`. It uses
   * the two-argument factory method, so this test uses the same one.
   *
   * The test releases the write-intent lock, because `Dispatchers.UI` holds none. The release is what gives
   * the test its reach. `ReadAction.computeBlocking` returns the value directly while read access is allowed,
   * so it reports no acquisition to a caller that still holds the lock. Only a released lock sends it through
   * `runReadAction`, where `NestedLocksThreadingSupport.handleLockAccess` reports it.
   *
   * The test watches the platform. A lock that a plugin `EditorFactoryListener` takes belongs to that plugin,
   * and Ultimate loads about 40 listeners that Community does not.
   */
  @Test
  fun testTheEditorIsBuiltWithoutTheRwLock(): Unit = fixture.runTest {
    initText("abc")
    // Only the file branch of the indent options reads the PSI, so the test needs a document with a file.
    assertNotNull(editor.virtualFile)
    val document = editor.document

    val reported = ArrayList<Throwable>()
    // `Dispatchers.UI` holds no lock, so the test must release the one it holds.
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
      val editorFactory = EditorFactory.getInstance()
      // This is what `Dispatchers.UI` does. It reports every lock, and it lets the lock proceed.
      val created = ApplicationManagerEx.getApplicationEx().withLocksSoftlyProhibited(
        "an editor must be built without the RW lock", { reported.add(it) }) {
        editorFactory.createEditor(document, project)
      }
      editorFactory.releaseEditor(created)
    }

    val platformLocks = reported.filter { it.acquisitionSite()?.startsWith("$EDITOR_IMPL_PACKAGE.") == true }
    assertTrue(platformLocks.isEmpty(),
               "the platform took a lock while the editor was built: ${platformLocks.map { it.stackTraceToString() }}")
  }

  /**
   * A code-style change must reach the tab character.
   *
   * The event is what makes this work, and not only through the cache: the computation prefers the
   * `CODE_STYLE_SETTINGS` user data of the editor over the project settings, and
   * `EditorImpl.codeStyleSettingsChanged` is what refreshes that user data and calls
   * `SettingsImpl.reinitSettings`. Without the event the pinned settings hide the new ones.
   *
   * This does not cover the *cache* invalidation. `EditorSettingsState.currentUseTabCharacter` reads no cache
   * in unit-test mode, so there is no cached value here to invalidate.
   */
  @Test
  fun testCodeStyleChangeReachesTheTabCharacter(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings
    val original = settings.isUseTabCharacter(project)

    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.USE_TAB_CHARACTER = !original
    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      manager.fireCodeStyleSettingsChanged()
      assertEquals(!original, settings.isUseTabCharacter(project))
    })

    manager.fireCodeStyleSettingsChanged()
    assertEquals(original, settings.isUseTabCharacter(project))
  }

  /**
   * The state property carries the value to the Remote Development frontend, which computes none of its own.
   * Its default must therefore be the project setting, and not the global one.
   */
  @Test
  fun testTheTabCharacterStateDefaultIsTheProjectSetting(): Unit = fixture.runTest {
    initText("abc")
    val projectValue = !CodeStyleSettings.getDefaults().indentOptions.USE_TAB_CHARACTER
    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.USE_TAB_CHARACTER = projectValue

    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      val editorFactory = EditorFactory.getInstance()
      val created = editorFactory.createEditor(editor.document, project)
      try {
        assertEquals(projectValue, (created.settings as SettingsImpl).getState().myUseTabCharacter)
      }
      finally {
        editorFactory.releaseEditor(created)
      }
    })
  }

  /**
   * The tab size follows the same rule as the tab character in [testTheTabCharacterStateDefaultIsTheProjectSetting].
   */
  @Test
  fun testTheTabSizeStateDefaultIsTheProjectSetting(): Unit = fixture.runTest {
    initText("abc")
    val projectValue = CodeStyleSettings.getDefaults().indentOptions.TAB_SIZE + 3
    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.TAB_SIZE = projectValue

    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      val editorFactory = EditorFactory.getInstance()
      val created = editorFactory.createEditor(editor.document, project)
      try {
        assertEquals(projectValue, (created.settings as SettingsImpl).getState().tabSize)
      }
      finally {
        editorFactory.releaseEditor(created)
      }
    })
  }

  /**
   * A code-style change must reach the state property, because the state property is what the backend sends to
   * the Remote Development frontend.
   */
  @Test
  fun testCodeStyleChangeReachesTheTabSizeState(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val original = settings.getTabSize(project)
    val changed = original + 3

    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.TAB_SIZE = changed
    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      manager.fireCodeStyleSettingsChanged()
      assertEquals(changed, settings.getState().tabSize)
      assertEquals(changed, settings.getTabSize(project))
    })

    manager.fireCodeStyleSettingsChanged()
    assertEquals(original, settings.getState().tabSize)
  }

  /**
   * An explicit tab size overrides the computed one, so a code-style change must not replace it.
   */
  @Test
  fun testAnExplicitTabSizeSurvivesCodeStyleChange(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val explicit = settings.getTabSize(project) + 5
    settings.setTabSize(explicit)

    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged()

    val state = settings.getState()
    assertTrue(state.isOverridden(state::tabSize))
    assertEquals(explicit, state.tabSize)
    assertEquals(explicit, settings.getTabSize(project))
  }

  /**
   * Settings without an editor take the tab size from the project of the caller, and an explicit tab size overrides it.
   */
  @Test
  fun testSettingsWithoutAnEditorUseTheProjectOfTheCaller(): Unit = fixture.runTest {
    val projectValue = CodeStyleSettings.getDefaults().indentOptions.TAB_SIZE + 3
    val manager = CodeStyleSettingsManager.getInstance(project)
    val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
    temporary.indentOptions.TAB_SIZE = projectValue

    CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
      val settings = SettingsImpl()
      assertEquals(projectValue, settings.getTabSize(project))

      val explicit = projectValue + 5
      settings.setTabSize(explicit)
      assertEquals(explicit, settings.getTabSize(project))
    })
  }
}
