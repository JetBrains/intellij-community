// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contract of [SettingsImpl] as [com.intellij.openapi.editor.EditorSettings] defines it, and the events that keep
 * its [EditorSettingsState] up to date.
 */
@TestApplication
class SettingsImplTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  /**
   * A Java caller can pass the project of a project-less editor, which is `null`.
   */
  @Test
  fun testNullProjectIsAllowed(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings

    assertEquals(settings.isUseTabCharacter(project), settings.isUseTabCharacter(null))
    assertEquals(settings.isWrapWhenTypingReachesRightMargin(project), settings.isWrapWhenTypingReachesRightMargin(null))
  }

  /**
   * `null` restores the soft margins of the code style, and an empty list means no soft margins.
   */
  @Test
  fun testNullSoftMarginsRestoreTheCodeStyleMargins(): Unit = fixture.runTest {
    initText("abc")
    val settings = editor.settings as SettingsImpl
    val codeStyleMargins = settings.softMargins

    // The getter is not nullable and the setter is, so a property assignment does not compile.
    @Suppress("UsePropertyAccessSyntax")
    settings.setSoftMargins(listOf(17, 42))
    assertEquals(listOf(17, 42), settings.softMargins)

    settings.setSoftMargins(null)
    assertEquals(codeStyleMargins, settings.softMargins)
    val state = settings.getState()
    assertFalse(state.isOverridden(state::softMargins))
  }

  /**
   * A change of the global setting reaches an open editor without a reinit of all editors.
   */
  @Test
  fun testHighlightSelectionOccurrencesFollowsTheGlobalSetting(): Unit = fixture.runTest {
    initText("abc")
    val global = EditorSettingsExternalizable.getInstance()
    val original = global.isHighlightSelectionOccurrences
    try {
      global.isHighlightSelectionOccurrences = !original
      assertEquals(!original, editor.settings.isHighlightSelectionOccurrences)
    } finally {
      global.isHighlightSelectionOccurrences = original
    }
  }

  /**
   * A reinit follows a rename and a file type change, so it must compute the language again.
   */
  @Test
  fun testReinitComputesTheLanguageAgain(): Unit = fixture.runTest {
    initText("abc")
    val language = switchableLanguage(editor.settings as SettingsImpl)
    withStickyLinesHiddenFor(Language.ANY) {
      language.current = Language.ANY
      (editor as EditorImpl).reinitSettings()
      assertFalse((editor.settings as SettingsImpl).getState().myStickyLinesShownForLanguage)
    }
  }

  /**
   * An editor without `EditorHighlighterUpdater` gets a file type change only from the state, so the state must
   * compute the language again.
   */
  @Test
  fun testFileTypeChangeComputesTheLanguageAgain(): Unit = fixture.runTest {
    initText("abc")
    val language = switchableLanguage(editor.settings as SettingsImpl)
    withStickyLinesHiddenFor(Language.ANY) {
      language.current = Language.ANY
      ApplicationManager.getApplication().runWriteAction {
        FileTypeManagerEx.getInstanceEx().makeFileTypesChange("test", EmptyRunnable.getInstance())
      }
      assertFalse((editor.settings as SettingsImpl).getState().myStickyLinesShownForLanguage)
    }
  }

  /**
   * A main editor uses soft wraps only for a file whose name matches a mask. A change of the masks reaches an open
   * editor without a reinit of all editors.
   */
  @Test
  fun testSoftWrapFileMasksReachTheEditor(): Unit = fixture.runTest {
    initText("abc")
    val global = EditorSettingsExternalizable.getInstance()
    val originalUse = global.isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR)
    val originalMasks = global.softWrapFileMasks
    try {
      global.setUseSoftWraps(true, SoftWrapAppliancePlaces.MAIN_EDITOR)
      global.softWrapFileMasks = "*.none"
      assertFalse(editor.settings.isUseSoftWraps)

      global.softWrapFileMasks = "*.txt"
      assertTrue(editor.settings.isUseSoftWraps)
    } finally {
      global.softWrapFileMasks = originalMasks
      global.setUseSoftWraps(originalUse, SoftWrapAppliancePlaces.MAIN_EDITOR)
    }
  }

  /**
   * A language that the test can switch. The state reads it only when it computes the language.
   */
  private class SwitchableLanguage(var current: Language)

  private fun switchableLanguage(settings: SettingsImpl): SwitchableLanguage {
    val language = SwitchableLanguage(PlainTextLanguage.INSTANCE)
    settings.setLanguageSupplier { language.current }
    return language
  }

  /**
   * Runs [test] while sticky lines are hidden for [language], and checks that they are shown before that.
   */
  private fun EditorModelFixture.withStickyLinesHiddenFor(language: Language, test: () -> Unit) {
    val global = EditorSettingsExternalizable.getInstance()
    global.setStickyLinesShownFor(language.id, false)
    try {
      assertTrue((editor.settings as SettingsImpl).getState().myStickyLinesShownForLanguage)
      test()
    } finally {
      global.setStickyLinesShownFor(language.id, true)
    }
  }
}
