// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.frame.OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID
import com.intellij.agent.workbench.sessions.sleep.PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBCheckBox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.JComponent

@TestApplication
class AgentWorkbenchSettingsConfigurableTest {
  @Test
  fun descriptorRegistersToolsConfigurable() {
    assertThat(sessionsDescriptor())
      .contains("<applicationConfigurable")
      .contains("instance=\"com.intellij.agent.workbench.sessions.settings.AgentWorkbenchSettingsConfigurable\"")
      .contains("id=\"${AgentWorkbenchSettingsConfigurable.ID}\"")
      .contains("key=\"settings.agent.workbench.name\"")
      .contains("parentId=\"tools\"")
  }

  @Test
  fun configurableAppliesAdvancedSettings(@TestDisposable disposable: Disposable) {
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, false, disposable)
    advancedSettings.setSetting(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID, true, disposable)

    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        val dedicatedFrameCheckBox = component.checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame"))
        val sleepPreventionCheckBox = component.checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"))

        assertThat(dedicatedFrameCheckBox.isSelected).isFalse()
        assertThat(sleepPreventionCheckBox.isSelected).isTrue()
        assertThat(configurable.isModified).isFalse()

        dedicatedFrameCheckBox.isSelected = true
        sleepPreventionCheckBox.isSelected = false

        assertThat(configurable.isModified).isTrue()
        configurable.apply()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()
    assertThat(AdvancedSettings.getBoolean(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID)).isFalse()
  }

  private fun JComponent.checkBox(text: String): JBCheckBox {
    return componentsOfType(JBCheckBox::class.java).single { it.text == text }
  }

  private fun <T : JComponent> JComponent.componentsOfType(componentClass: Class<T>): List<T> {
    val result = mutableListOf<T>()
    collectComponentsOfType(this, componentClass, result)
    return result
  }

  private fun <T : JComponent> collectComponentsOfType(parent: Container, componentClass: Class<T>, result: MutableList<T>) {
    if (componentClass.isInstance(parent)) {
      result += componentClass.cast(parent)
    }
    for (component in parent.components) {
      if (component is Container) {
        collectComponentsOfType(component, componentClass, result)
      }
    }
  }

  private fun sessionsDescriptor(): String {
    return checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()
  }
}
