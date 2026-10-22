// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/** The packages of the editor settings and of the code style that the settings read. */
private val SETTINGS_PACKAGES = listOf(
  "com.intellij.openapi.editor.impl.",
  "com.intellij.application.options.",
  "com.intellij.psi.codeStyle.",
)

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
 * while the editor is built. See IJPL-243574. `EditorSettingsState` computes its inputs in a background read action
 * instead, so these tests also cover where these values come from.
 */
@TestApplication
class EditorSettingsLockFreeCreationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private const val TIMEOUT_SECONDS = 30L
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
   * The test watches the editor settings and the code style that they read. A lock that a plugin
   * `EditorFactoryListener` takes belongs to that plugin, and Ultimate loads about 40 listeners that Community does not.
   *
   * The inputs compute in the background, as in production. In unit-test mode they compute at once, and that takes
   * the lock on purpose.
   */
  @Test
  fun testTheEditorIsBuiltWithoutTheRwLock(): Unit = fixture.runTest {
    EditorSettingsState.computeInputsInBackgroundInTests(testRootDisposable)
    initText("abc")
    // Only the file branch of the indent options reads the PSI, so the test needs a document with a file. Nobody asked
    // for the code style of this file yet, so the code style cache cannot hide a computation.
    val freshFile = this.fixture.addFileToProject("fresh.txt", "abc")
    val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(freshFile))

    val reported = ArrayList<Throwable>()
    // The fixture uses temporary code style settings, and they skip the code style cache. A file computes its settings
    // in that cache, under the lock, so the test drops them while the editor is built.
    val temporarySettings = CodeStyleSettingsManager.getInstance(project).temporarySettings
    CodeStyle.dropTemporarySettings(project)
    try {
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
    } finally {
      if (temporarySettings != null) {
        CodeStyle.setTemporarySettings(project, temporarySettings)
      }
    }

    val platformLocks = reported.filter { error ->
      val site = error.acquisitionSite()
      site != null && SETTINGS_PACKAGES.any { site.startsWith(it) }
    }
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
   * An editor that `EditorFactory` builds has no `EditorHighlighterUpdater`, so only the state sees a file type change.
   * The test changes the code style without an event, so only the file type change can bring the new value.
   */
  @Test
  fun testFileTypeChangeReachesAnEditorWithoutAHighlighterUpdater(): Unit = fixture.runTest {
    initText("abc")
    val editorFactory = EditorFactory.getInstance()
    val created = editorFactory.createEditor(editor.document, project)
    try {
      val state = (created.settings as SettingsImpl).getState()
      val changed = state.tabSize + 3
      val manager = CodeStyleSettingsManager.getInstance(project)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
      temporary.indentOptions.TAB_SIZE = changed

      CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
        ApplicationManager.getApplication().runWriteAction {
          FileTypeManagerEx.getInstanceEx().makeFileTypesChange("test", EmptyRunnable.getInstance())
        }
        assertEquals(changed, state.tabSize)
      })
    }
    finally {
      editorFactory.releaseEditor(created)
    }
  }

  /**
   * A viewer takes its tab size from the file type, so a change of the viewer flag computes the tab size again.
   */
  @Test
  fun testViewerChangeComputesTheTabSizeAgain(): Unit = fixture.runTest {
    initText("abc")
    val editorFactory = EditorFactory.getInstance()
    val created = editorFactory.createEditor(editor.document, project) as EditorImpl
    try {
      val state = (created.settings as SettingsImpl).getState()
      val changed = state.tabSize + 3
      val manager = CodeStyleSettingsManager.getInstance(project)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
      temporary.indentOptions.TAB_SIZE = changed

      CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
        created.isViewer = true
        assertEquals(changed, state.tabSize)
      })
    }
    finally {
      editorFactory.releaseEditor(created)
    }
  }

  /**
   * A console can set the renderer mode on a background thread. The new tab size must still reach the editor on the EDT,
   * because a changed tab size refreshes the editor.
   */
  @Test
  fun testARendererModeChangeOffTheEdtComputesTheTabSizeAgain(): Unit = fixture.runTest {
    initText("abc")
    val editorFactory = EditorFactory.getInstance()
    val created = editorFactory.createEditor(editor.document, project) as EditorImpl
    try {
      val state = (created.settings as SettingsImpl).getState()
      val changed = state.tabSize + 3
      val manager = CodeStyleSettingsManager.getInstance(project)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
      temporary.indentOptions.TAB_SIZE = changed

      CodeStyle.doWithTemporarySettings(project, temporary, Runnable {
        ApplicationManager.getApplication().executeOnPooledThread {
          created.setRendererMode(true)
        }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        PlatformTestUtil.waitWithEventsDispatching("The tab size did not reach the editor", { state.tabSize == changed },
                                                   TIMEOUT_SECONDS.toInt())
      })
    }
    finally {
      editorFactory.releaseEditor(created)
    }
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
