// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionCostPresentationSettings
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.TestAgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.settings.AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsComponent
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributor
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributors
import com.intellij.agent.workbench.sessions.sleep.PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Container
import javax.swing.JComponent

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchSettingsConfigurableTest {
  @BeforeEach
  fun setUp() {
    resetSettings()
  }

  @AfterEach
  fun tearDown() {
    resetSettings()
  }

  private fun resetSettings() {
    service<AgentSessionProviderSettingsService>().setProviderEnabled(AgentSessionProvider.CODEX, true)
    AgentWorkbenchSettings.getInstance().loadState(AgentWorkbenchSettings.SettingsState())
    AgentSessionCostPresentationSettings.setEnabled(false)
    setJbCentralQuotaWidgetEnabled(false)
  }

  @Test
  fun descriptorRegistersToolsConfigurable() {
    assertThat(sessionsDescriptor())
      .contains("<applicationConfigurable")
      .contains("instance=\"com.intellij.agent.workbench.sessions.settings.AgentWorkbenchSettingsConfigurable\"")
      .contains("id=\"${AgentWorkbenchSettingsConfigurable.ID}\"")
      .contains("key=\"settings.agent.workbench.name\"")
      .contains("parentId=\"tools\"")
      .contains("instance=\"com.intellij.agent.workbench.sessions.settings.AgentWorkbenchProvidersSettingsConfigurable\"")
      .contains("id=\"${AgentWorkbenchProvidersSettingsConfigurable.ID}\"")
      .contains("key=\"settings.agent.workbench.providers.name\"")
      .contains("parentId=\"${AgentWorkbenchSettingsConfigurable.ID}\"")
      .contains("<applicationSettings service=\"com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService\"/>")
  }

  @Test
  fun configurableAppliesSettings(@TestDisposable disposable: Disposable) {
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    AgentWorkbenchSettings.getInstance().setOpenInDedicatedFrame(false)
    AgentWorkbenchSettings.getInstance().setShowAgentActivityInMainToolbar(false)
    advancedSettings.setSetting(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID, true, disposable)

    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        val dedicatedFrameCheckBox =
          component.checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame"))
        val mainToolbarActivityCheckBox =
          component.checkBox(AgentSessionsBundle.message("settings.agent.workbench.show.activity.in.main.toolbar"))
        val sleepPreventionCheckBox =
          component.checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"))

        assertThat(dedicatedFrameCheckBox.isSelected).isFalse()
        assertThat(mainToolbarActivityCheckBox.isSelected).isFalse()
        assertThat(sleepPreventionCheckBox.isSelected).isTrue()
        assertThat(configurable.isModified).isFalse()

        dedicatedFrameCheckBox.isSelected = true
        mainToolbarActivityCheckBox.isSelected = true
        sleepPreventionCheckBox.isSelected = false

        assertThat(configurable.isModified).isTrue()
        configurable.apply()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrame).isTrue()
    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrameOverride).isNull()
    assertThat(AgentWorkbenchSettings.getInstance().showAgentActivityInMainToolbar).isTrue()
    assertThat(AgentWorkbenchSettings.getInstance().showAgentActivityInMainToolbarOverride).isTrue()
    assertThat(AdvancedSettings.getBoolean(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID)).isFalse()
  }

  @Test
  fun configurableRendersRegisteredSettingsContributors(@TestDisposable disposable: Disposable) {
    AgentWorkbenchSettingsContributors.EP_NAME.point.registerExtension(TestSettingsContributor(), disposable)

    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        assertThat(component.checkBox(TEST_CONTRIBUTOR_CHECKBOX_TEXT)).isNotNull
      }
      finally {
        configurable.disposeUIResources()
      }
    }
  }

  @Test
  fun configurableRendersAndAppliesSessionCostSetting() {
    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        val checkbox = component.checkBox(AgentSessionsBundle.message("settings.agent.workbench.session.cost"))
        assertThat(checkbox.isSelected).isFalse()

        checkbox.isSelected = true
        assertThat(configurable.isModified).isTrue()

        configurable.apply()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

    assertThat(AgentSessionCostPresentationSettings.isEnabled()).isTrue()
  }

  @Test
  fun configurableRendersAndAppliesJbCentralQuotaSetting() {
    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        assertThat(component.titledSeparatorTexts())
          .containsSubsequence(
            AgentSessionsBundle.message("settings.agent.workbench.chat.group"),
            AgentSessionsBundle.message("settings.agent.workbench.general.group"),
            AgentSessionsBundle.message("settings.agent.workbench.status.bar.widgets.group"),
          )

        val checkbox = component.checkBox(AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget"))
        assertThat(checkbox.isSelected).isFalse()

        checkbox.isSelected = true
        assertThat(configurable.isModified).isTrue()

        configurable.apply()
      }
      finally {
        configurable.disposeUIResources()
      }
    }

    assertThat(isJbCentralQuotaWidgetEnabled()).isTrue()
  }

  @Test
  fun configurableGroupsRegisteredChatSettingsComponentWithDedicatedFrameSetting(@TestDisposable disposable: Disposable) {
    AgentWorkbenchSettingsContributors.EP_NAME.point.registerExtension(TestChatSettingsComponentContributor(), disposable)

    runInEdtAndWait {
      val configurable = AgentWorkbenchSettingsConfigurable()
      try {
        val component = configurable.createComponent()
        configurable.reset()

        assertThat(component.componentsOfType(JBCheckBox::class.java).map { it.text })
          .containsSubsequence(
            AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame"),
            TEST_CHAT_COMPONENT_CHECKBOX_TEXT,
            AgentSessionsBundle.message("settings.agent.workbench.show.activity.in.main.toolbar"),
            AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"),
          )
      }
      finally {
        configurable.disposeUIResources()
      }
    }
  }

  @Test
  fun configurableAppliesProviderSettings() {
    val providerSettings = service<AgentSessionProviderSettingsService>()
    providerSettings.setProviderEnabled(AgentSessionProvider.CODEX, true)

    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(
        listOf(
          TestAgentSessionProviderDescriptor(
            provider = AgentSessionProvider.CODEX,
            supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
            cliAvailable = true,
          )
        )
      )
    ) {
      runInEdtAndWait {
        val configurable = AgentWorkbenchProvidersSettingsConfigurable()
        try {
          val component = configurable.createComponent()
          configurable.reset()

          val providerCheckBox = component.checkBox(AgentSessionsBundle.message("toolwindow.provider.codex"))
          assertThat(providerCheckBox.isSelected).isTrue()

          providerCheckBox.isSelected = false
          assertThat(configurable.isModified).isTrue()
          configurable.apply()
        }
        finally {
          configurable.disposeUIResources()
        }
      }
    }

    assertThat(providerSettings.isProviderEnabled(AgentSessionProvider.CODEX)).isFalse()
  }

  @Test
  fun configurableAppliesProviderFeatureSettings() {
    var providerFeatureEnabled = true

    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(
        listOf(
          TestAgentSessionProviderDescriptor(
            provider = AgentSessionProvider.CODEX,
            supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
            cliAvailable = true,
            providerSettings = listOf(
              AgentWorkbenchCheckboxSetting(
                text = TEST_PROVIDER_FEATURE_CHECKBOX_TEXT,
                description = TEST_PROVIDER_FEATURE_CHECKBOX_DESCRIPTION,
                isSelected = { providerFeatureEnabled },
                setSelected = { enabled -> providerFeatureEnabled = enabled },
              )
            ),
          )
        )
      )
    ) {
      runInEdtAndWait {
        val configurable = AgentWorkbenchProvidersSettingsConfigurable()
        try {
          val component = configurable.createComponent()
          configurable.reset()

          val providerFeatureCheckBox = component.checkBox(TEST_PROVIDER_FEATURE_CHECKBOX_TEXT)
          assertThat(providerFeatureCheckBox.isSelected).isTrue()

          providerFeatureCheckBox.isSelected = false
          assertThat(configurable.isModified).isTrue()
          configurable.apply()
        }
        finally {
          configurable.disposeUIResources()
        }
      }
    }

    assertThat(providerFeatureEnabled).isFalse()
  }

  private fun JComponent.checkBox(text: String): JBCheckBox {
    return componentsOfType(JBCheckBox::class.java).single { it.text == text }
  }

  private fun JComponent.titledSeparatorTexts(): List<String> {
    return componentsOfType(TitledSeparator::class.java).map { it.text }
  }

  private fun isJbCentralQuotaWidgetEnabled(): Boolean {
    return StatusBarWidgetSettings.getInstance().isEnabled(jbCentralQuotaWidgetFactory())
  }

  private fun setJbCentralQuotaWidgetEnabled(enabled: Boolean) {
    StatusBarWidgetSettings.getInstance().setEnabled(jbCentralQuotaWidgetFactory(), enabled)
  }

  private fun jbCentralQuotaWidgetFactory(): StatusBarWidgetFactory {
    return checkNotNull(StatusBarWidgetFactory.EP_NAME.extensionList.firstOrNull { it.id == JBCENTRAL_QUOTA_WIDGET_ID }) {
      "Missing status bar widget factory: $JBCENTRAL_QUOTA_WIDGET_ID"
    }
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

  private class TestSettingsContributor : AgentWorkbenchSettingsContributor {
    override fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> {
      return listOf(
        AgentWorkbenchCheckboxSetting(
          text = TEST_CONTRIBUTOR_CHECKBOX_TEXT,
          description = null,
          isSelected = { false },
          setSelected = {},
        )
      )
    }
  }

  private class TestChatSettingsComponentContributor : AgentWorkbenchSettingsContributor {
    override fun components(): List<AgentWorkbenchSettingsComponent> {
      return listOf(
        AgentWorkbenchSettingsComponent(
          id = AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID,
          displayName = "Chat",
          checkboxSettings = listOf(
            AgentWorkbenchCheckboxSetting(
              text = TEST_CHAT_COMPONENT_CHECKBOX_TEXT,
              description = null,
              isSelected = { false },
              setSelected = {},
            )
          ),
        )
      )
    }
  }

  companion object {
    private const val JBCENTRAL_QUOTA_WIDGET_ID = "jbcentral.quota"
    private const val TEST_CONTRIBUTOR_CHECKBOX_TEXT = "Test provider setting"
    private const val TEST_CHAT_COMPONENT_CHECKBOX_TEXT = "Test chat setting"
    private const val TEST_PROVIDER_FEATURE_CHECKBOX_TEXT = "Test provider feature"
    private const val TEST_PROVIDER_FEATURE_CHECKBOX_DESCRIPTION = "Test provider feature description"
  }
}
