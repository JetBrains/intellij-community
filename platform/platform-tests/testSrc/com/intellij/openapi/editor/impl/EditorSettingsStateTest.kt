// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.registry.EditorRegistry
import com.intellij.openapi.util.registry.Registry
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
 * The settings of the rounded corners and of the hugging extent of the highlighting. The [SettingsSnapshot] holds the
 * choices of the user, and [EditorImpl.isRoundedHighlighting] and [EditorImpl.isHuggingHighlighting] give the values.
 */
@TestApplication
class EditorSettingsStateTest {
  companion object {
    private const val OLD_SELECTION_KEY = "editor.old.full.horizontal.selection.enabled"

    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  private val settings: EditorSettingsExternalizable
    get() = EditorSettingsExternalizable.getInstance()

  private var oldRoundedOption: Boolean? = null
  private var oldHuggingOption: Boolean? = null
  private var oldIslands: Boolean = false
  private var oldFullSelection: Boolean = false

  @BeforeEach
  fun saveTheSettings() {
    oldRoundedOption = settings.roundedHighlightingOption
    oldHuggingOption = settings.huggingHighlightingOption
    oldIslands = IslandsState.isEnabled()
    oldFullSelection = Registry.get(OLD_SELECTION_KEY).asBoolean()
    Registry.get(OLD_SELECTION_KEY).setValue(false)
  }

  @AfterEach
  fun restoreTheSettings() {
    settings.roundedHighlightingOption = oldRoundedOption
    settings.huggingHighlightingOption = oldHuggingOption
    IslandsState.setEnabled(oldIslands, false)
    Registry.get(OLD_SELECTION_KEY).setValue(oldFullSelection)
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

  @Test
  fun testTheThemeGivesTheHuggingWithoutAnOption(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = null
    IslandsState.setEnabled(true, false)
    initText("abc")

    assertTrue(editorHugs())
  }

  @Test
  fun testAClassicThemeGivesNoHuggingWithoutAnOption(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = null
    IslandsState.setEnabled(false, false)
    initText("abc")

    assertFalse(editorHugs())
  }

  @Test
  fun testTheHuggingOptionWinsOverTheTheme(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = true
    IslandsState.setEnabled(false, false)
    initText("abc")
    assertTrue(editorHugs())

    settings.huggingHighlightingOption = false
    IslandsState.setEnabled(true, false)
    assertFalse(editorHugs())
  }

  @Test
  fun testTheRegistryKeyTurnsTheHuggingOffOverTheOption(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = true
    initText("abc")
    Registry.get(OLD_SELECTION_KEY).setValue(true)

    assertFalse(editorHugs())
  }

  @Test
  fun testColumnModeTurnsTheHuggingOffOverTheOption(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = true
    initText("abc")
    (editor as EditorEx).isColumnMode = true

    assertFalse(editorHugs())
  }

  @Test
  fun testTheSnapshotKeepsTheHuggingChoiceOfTheUser(): Unit = fixture.runTest {
    settings.huggingHighlightingOption = null
    initText("abc")
    assertNull(settingsSnapshot().getHuggingHighlightingOption())

    settings.huggingHighlightingOption = true
    assertEquals(true, settingsSnapshot().getHuggingHighlightingOption())

    settings.huggingHighlightingOption = false
    assertEquals(false, settingsSnapshot().getHuggingHighlightingOption())
  }

  private fun EditorModelFixture.editorRounds(): Boolean {
    return (editor as EditorImpl).isRoundedHighlighting()
  }

  private fun EditorModelFixture.editorHugs(): Boolean {
    return (editor as EditorImpl).isHuggingHighlighting(EditorRegistry.current())
  }

  private fun EditorModelFixture.settingsSnapshot(): SettingsSnapshot {
    return (editor as EditorImpl).settingsSnapshot
  }
}
