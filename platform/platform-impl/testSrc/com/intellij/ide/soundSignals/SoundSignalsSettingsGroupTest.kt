// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.FocusEvent
import javax.swing.JComponent

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalsSettingsGroupTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the combo box offers only On and Off and shows the effective value`() = groupTest(SoundSignalsSettingsState()) { page ->
    assertThat((0 until page.playSignals.itemCount).map(page.playSignals::getItemAt)).containsExactly(true, false)
    assertThat(page.playSignals.item).isFalse()
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).isSelected).isTrue()
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).isEnabled).isFalse()

    page.screenReader.isSelected = true
    assertThat(page.playSignals.item).isTrue()
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).isEnabled).isTrue()

    assertThat(page.panel.isModified()).isFalse()
    assertThat(player.previewed).isEmpty()
  }

  @Test
  fun `apply without an edit writes no calculated value`() = groupTest(SoundSignalsSettingsState()) { page ->
    page.screenReader.isSelected = true
    page.panel.apply()
    page.screenReader.isSelected = false
    page.panel.apply()

    assertThat(service<AccessibilitySettings>().soundSignals).isEqualTo(SoundSignalsSettingsState())
  }

  @Test
  fun `a combo box edit records an explicit value`() = groupTest(SoundSignalsSettingsState()) { page ->
    page.screenReader.isSelected = true
    page.playSignals.selectedItem = false
    assertThat(page.panel.isModified()).isTrue()

    page.panel.apply()
    assertThat(service<AccessibilitySettings>().soundSignals).isEqualTo(SoundSignalsSettingsState(playSignals = false))

    page.playSignals.selectedItem = true
    page.panel.apply()
    assertThat(service<AccessibilitySettings>().soundSignals).isEqualTo(SoundSignalsSettingsState(playSignals = true))
  }

  @Test
  fun `an explicit child survives a screen reader toggle`() = groupTest(SoundSignalsSettingsState()) { page ->
    page.screenReader.isSelected = true
    page.signal(IdeSoundSignals.ERROR_LINE).doClick()

    page.screenReader.isSelected = false
    assertThat(page.playSignals.item).isFalse()
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).isSelected).isFalse()
    assertThat(page.signal(IdeSoundSignals.ERROR_CARET).isSelected).isTrue()

    page.screenReader.isSelected = true
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).isSelected).isFalse()

    page.panel.apply()
    assertThat(service<AccessibilitySettings>().soundSignals).isEqualTo(SoundSignalsSettingsState(signals = mapOf("error.line" to false)))
  }

  @Test
  fun `a choice of an id with no declaration survives an apply`() = groupTest(
    playingState().copy(signals = mapOf("plugin.only.signal" to false)),
  ) { page ->
    page.signal(IdeSoundSignals.ERROR_LINE).doClick()
    page.panel.apply()

    assertThat(service<AccessibilitySettings>().soundSignals.signals)
      .containsExactlyInAnyOrderEntriesOf(mapOf("plugin.only.signal" to false, "error.line" to false))
  }

  @Test
  fun `a group state follows its children`() = groupTest(
    playingState(disabled = listOf(IdeSoundSignals.ERROR_LINE, IdeSoundSignals.FOLDED_LINE, IdeSoundSignals.FOLDED_CARET)),
  ) { page ->
    assertThat(page.group(IdeSoundSignals.CODE_HIGHLIGHTING_GROUP).state).isEqualTo(State.DONT_CARE)
    assertThat(page.group(IdeSoundSignals.FOLDING_GROUP).state).isEqualTo(State.NOT_SELECTED)

    page.signal(IdeSoundSignals.ERROR_LINE).doClick()
    assertThat(page.group(IdeSoundSignals.CODE_HIGHLIGHTING_GROUP).state).isEqualTo(State.SELECTED)

    service<AccessibilitySettings>().setSignal(IdeSoundSignals.FOLDED_LINE, true)
    page.panel.reset()
    assertThat(page.group(IdeSoundSignals.CODE_HIGHLIGHTING_GROUP).state).isEqualTo(State.DONT_CARE)
    assertThat(page.group(IdeSoundSignals.FOLDING_GROUP).state).isEqualTo(State.DONT_CARE)
  }

  @Test
  fun `a group click toggles every child, stores explicit per-signal values and plays no preview, a child plays its preview`() = groupTest(
    playingState(disabled = listOf(IdeSoundSignals.WARNING_CARET)),
  ) { page ->
    val group = page.group(IdeSoundSignals.CODE_HIGHLIGHTING_GROUP)
    val children = CODE_HIGHLIGHTING_SIGNALS.map(page::signal)

    group.doClick()
    assertThat(group.state).isEqualTo(State.SELECTED)
    assertThat(children).allMatch { it.isSelected }

    group.doClick()
    assertThat(group.state).isEqualTo(State.NOT_SELECTED)
    assertThat(children).noneMatch { it.isSelected }

    group.doClick()
    assertThat(player.previewed).isEmpty()

    page.panel.apply()
    assertThat(service<AccessibilitySettings>().soundSignals.signals).containsExactlyInAnyOrderEntriesOf(CODE_HIGHLIGHTING_SIGNALS.associate { it.id to true })

    page.signal(IdeSoundSignals.ERROR_CARET).doClick()
    focusByTab(page.signal(IdeSoundSignals.WARNING_LINE))

    assertThat(player.previewed).containsExactly(listOf(IdeSoundSignals.ERROR_CARET), listOf(IdeSoundSignals.WARNING_LINE))
  }

  @Test
  fun `a child names its group in the accessible description`() = groupTest(playingState()) { page ->
    assertThat(page.signal(IdeSoundSignals.ERROR_LINE).accessibleContext.accessibleDescription)
      .isEqualTo("${IdeSoundSignals.CODE_HIGHLIGHTING_GROUP.title} group")
    assertThat(page.group(IdeSoundSignals.CODE_HIGHLIGHTING_GROUP).accessibleContext.accessibleDescription)
      .isEqualTo(IdeBundle.message("sound.signals.group.accessible.description"))
  }

  private lateinit var player: RecordingPlayer

  private fun playingState(disabled: List<SoundSignal> = emptyList()): SoundSignalsSettingsState =
    SoundSignalsSettingsState(playSignals = true, signals = disabled.associate { it.id to false })

  private fun groupTest(state: SoundSignalsSettingsState, body: (Page) -> Unit) {
    player = RecordingPlayer()
    ApplicationManager.getApplication().replaceService(SoundSignalPlayer::class.java, player, disposable)
    withSoundSignalsSettings { settings ->
      settings.loadSoundSignals(state)
      val screenReader = JBCheckBox("Support screen readers")
      val panel = panel { soundSignalsGroup(screenReader) }
      panel.reset()
      body(Page(panel, screenReader))
    }
  }

  private class Page(val panel: DialogPanel, val screenReader: JBCheckBox) {
    @Suppress("UNCHECKED_CAST")
    val playSignals: ComboBox<Boolean> = UIUtil.findComponentsOfType(panel, ComboBox::class.java).single() as ComboBox<Boolean>

    fun group(group: SoundSignalGroup): ThreeStateCheckBox =
      UIUtil.findComponentsOfType(panel, ThreeStateCheckBox::class.java).single { it.text == group.title }

    fun signal(signal: SoundSignal): JBCheckBox =
      UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).single { it.text == signal.title }
  }

  private fun focusByTab(component: JComponent) {
    val event = FocusEvent(component, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD)
    for (listener in component.focusListeners) listener.focusGained(event)
  }

  private class RecordingPlayer : SoundSignalPlayer() {
    val previewed = ArrayList<List<SoundSignal>>()

    override fun playEnabled(signals: Collection<SoundSignal>) {
      previewed += signals.toList()
    }
  }

  private companion object {
    val CODE_HIGHLIGHTING_SIGNALS = with(IdeSoundSignals) { listOf(ERROR_LINE, ERROR_CARET, WARNING_LINE, WARNING_CARET) }
  }
}
