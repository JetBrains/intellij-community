// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * An editor can be built on `Dispatchers.UI`, which forbids the RW lock. So the editor settings must
 * take no lock while the editor is built. See IJPL-243574. `SettingsImpl` answers for the tab character
 * from a background read action instead, so these tests also cover where that value comes from.
 */
class EditorSettingsLockFreeCreationTest : AbstractEditorTest() {
  /**
   * `EditorTextField` builds its editor from `addNotify`, and that can run on `Dispatchers.UI`. It uses
   * the two-argument factory method, so this test uses the same one.
   *
   * The test watches the editor settings only. `EditorImpl.setHighlighter` and two
   * `EditorFactoryListener` implementations still take a lock, and IJPL-243574 covers them.
   */
  fun testEditorSettingsTakeNoLockWhileTheEditorIsBuilt() {
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

    val settingsLocks = reported.filter { throwable ->
      throwable.stackTrace.any { it.className == SettingsImpl::class.java.name || it.className == EditorSettingsState::class.java.name }
    }
    assertTrue("the editor settings took a lock while the editor was built: ${settingsLocks.map { it.stackTraceToString() }}",
               settingsLocks.isEmpty())
  }

  /**
   * A code-style change must reach the tab character.
   *
   * The event is what makes this work, and not only through the cache: `computeValue` prefers the
   * `CODE_STYLE_SETTINGS` user data of the editor over the project settings, and
   * `EditorImpl.codeStyleSettingsChanged` is what refreshes that user data and calls
   * `SettingsImpl.reinitSettings`. Without the event the pinned settings hide the new ones.
   *
   * This does not cover the *cache* invalidation. `CacheableBackgroundComputable.getDefaultAndCompute`
   * keeps no cache in unit-test mode, so there is no cached value here to invalidate.
   */
  fun testCodeStyleChangeReachesTheTabCharacter() {
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
   * Its default must therefore be the project setting, and not the global one. `SettingsImpl` seeds its own
   * computable from the same expression, so the two sides open on one value.
   */
  fun testTheTabCharacterStateDefaultIsTheProjectSetting() {
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
}
