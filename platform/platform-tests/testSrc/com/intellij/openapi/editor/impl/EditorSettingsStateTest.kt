// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.IslandsState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The setting of the rounded corners of the highlighting. The [SettingsSnapshot] holds the choice of the user, and
 * [EditorImpl.isRoundedHighlighting] gives the value.
 */
@TestApplication
class EditorSettingsStateTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  private val settings: EditorSettingsExternalizable
    get() = EditorSettingsExternalizable.getInstance()

  private var oldRoundedOption: Boolean? = null
  private var oldIslands: Boolean = false

  @BeforeEach
  fun saveTheSettings() {
    oldRoundedOption = settings.roundedHighlightingOption
    oldIslands = IslandsState.isEnabled()
  }

  @AfterEach
  fun restoreTheSettings() {
    settings.roundedHighlightingOption = oldRoundedOption
    IslandsState.setEnabled(oldIslands, false)
  }

  @Test
  fun testTheThemeGivesTheRoundingWithoutAnOption(): Unit = fixture.runTest {
    settings.roundedHighlightingOption = null
    IslandsState.setEnabled(true, false)
    initText("abc")

    assertTrue(editorRounds())
  }

  @Test
  fun testAClassicThemeGivesNoRoundingWithoutAnOption(): Unit = fixture.runTest {
    settings.roundedHighlightingOption = null
    IslandsState.setEnabled(false, false)
    initText("abc")

    assertFalse(editorRounds())
  }

  @Test
  fun testTheOptionWinsOverTheTheme(): Unit = fixture.runTest {
    settings.roundedHighlightingOption = true
    IslandsState.setEnabled(false, false)
    initText("abc")
    assertTrue(editorRounds())

    settings.roundedHighlightingOption = false
    IslandsState.setEnabled(true, false)
    assertFalse(editorRounds())
  }

  @Test
  fun testTheRoundingFollowsTheThemeWithoutARefresh(): Unit = fixture.runTest {
    settings.roundedHighlightingOption = null
    IslandsState.setEnabled(true, false)
    initText("abc")
    assertTrue(editorRounds())

    IslandsState.setEnabled(false, false)
    assertFalse(editorRounds())
  }

  @Test
  fun testTheSnapshotKeepsTheChoiceOfTheUser(): Unit = fixture.runTest {
    settings.roundedHighlightingOption = null
    IslandsState.setEnabled(true, false)
    initText("abc")
    assertNull(settingsSnapshot().getRoundedHighlightingOption())

    settings.roundedHighlightingOption = true
    assertEquals(true, settingsSnapshot().getRoundedHighlightingOption())

    settings.roundedHighlightingOption = false
    assertEquals(false, settingsSnapshot().getRoundedHighlightingOption())
  }

  private fun EditorModelFixture.editorRounds(): Boolean {
    return (editor as EditorImpl).isRoundedHighlighting()
  }

  private fun EditorModelFixture.settingsSnapshot(): SettingsSnapshot {
    return (editor as EditorImpl).settingsSnapshot
  }
}
