// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.platform.settings.v2.impl.SettingsV2Bundle
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the Go to Action options that toggle [EDIT_TEAM_LAYER]. The test finds them through the real extension points,
 * where `intellij.platform.settings.v2.backend.baseline.xml` registers them. Each test gets its own project, so no
 * project value leaks between the tests. The application value is shared by the whole test application, so each test
 * waits for the writes of its options and [restoreApplicationMode] then writes the default back.
 */
@TestApplication
class SettingsTeamEditingModeOptionsTest {
  companion object {
    /** Holds the key of the [GatedKindLayerProvider] that holds a reload. The provider supplies no layer of it. */
    private val gateKey = SettingsKey("test.teamEditingModeOptions.gate", default = "def")
  }

  private val projectFixture = projectFixture(openAfterCreation = true)

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val appContext get() = SettingsContext.app()
  private val projectContext get() = SettingsContext.project(project)

  @AfterEach
  fun restoreApplicationMode(): Unit = runBlocking {
    EDIT_TEAM_LAYER[appContext] = false
  }

  @Test
  fun `the application option toggles the mode at the application context`(): Unit = runBlocking {
    val option = applicationOption()
    EDIT_TEAM_LAYER[appContext] = false
    assertFalse(option.isOptionEnabled)
    EDIT_TEAM_LAYER[appContext] = true
    assertTrue(option.isOptionEnabled)

    withContext(Dispatchers.EDT) { option.setOptionState(false) }

    awaitEquals(false) { EDIT_TEAM_LAYER[appContext] }
    awaitEquals(false) { option.isOptionEnabled }
  }

  @Test
  fun `the project option is on when only the application mode is on`(): Unit = runBlocking {
    val option = projectOption()
    EDIT_TEAM_LAYER[appContext] = false
    assertFalse(option.isOptionEnabled)

    EDIT_TEAM_LAYER[appContext] = true

    assertTrue(option.isOptionEnabled)
  }

  @Test
  fun `turning the project option off writes an explicit false at the project`(): Unit = runBlocking {
    val option = projectOption()
    EDIT_TEAM_LAYER[appContext] = true
    assertTrue(option.isOptionEnabled)

    withContext(Dispatchers.EDT) { option.setOptionState(false) }

    awaitEquals(false) { EDIT_TEAM_LAYER[projectContext] }
    awaitEquals(false) { option.isOptionEnabled }
    assertTrue(EDIT_TEAM_LAYER[appContext])
  }

  /**
   * Holds a reload of the application scope with a closed gate, so the write of the option cannot end before the
   * asserts. The gate opens also on a failure, because a held reload blocks the reloads of the other tests.
   */
  @Test
  fun `an option reports the requested state right after the request`(): Unit = runBlocking {
    val option = applicationOption()
    EDIT_TEAM_LAYER[appContext] = false
    val gatedProvider = GatedKindLayerProvider(APPLICATION_KINDS, gateKey)
    registerGatedProvider(disposable, gatedProvider)
    val gate = gatedProvider.closeGate()
    try {
      launch { SettingsVault.getInstance().reload(appContext) }
      awaitEquals(false) { appContext.isReady() }  // so the write of the option waits for the reload (ADR 0063)

      withContext(Dispatchers.EDT) {
        option.setOptionState(true)
        assertTrue(option.isOptionEnabled)
      }
      assertFalse(EDIT_TEAM_LAYER[appContext])
    }
    finally {
      gate.complete(Unit)
    }

    awaitEquals(true) { EDIT_TEAM_LAYER[appContext] }
  }

  private fun applicationOption(): BooleanOptionDescription =
    SearchTopHitProvider.EP_NAME.extensionList
      .filterIsInstance<OptionsSearchTopHitProvider.ApplicationLevelProvider>()
      .flatMap { it.options }
      .booleanOption(SettingsV2Bundle.message("search.option.team.editing.mode.application"))

  private fun projectOption(): BooleanOptionDescription =
    OptionsTopHitProvider.PROJECT_LEVEL_EP.extensionList
      .flatMap { it.getOptions(project) }
      .booleanOption(SettingsV2Bundle.message("search.option.team.editing.mode.project"))

  /** Returns the only boolean option with the [label]. */
  private fun List<OptionDescription>.booleanOption(label: String): BooleanOptionDescription =
    filterIsInstance<BooleanOptionDescription>().single { it.option == label }
}
