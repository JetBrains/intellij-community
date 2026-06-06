// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.TestAgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentProviderCliStatusBanner
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentProviderCliStatusBannerTest {
  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  private val providerSettings: AgentSessionProviderSettingsService
    get() = service()

  private val availabilityService: AgentSessionProviderAvailabilityService
    get() = project.service()

  @AfterEach
  fun resetState() {
    providerSettings.setProviderEnabled(AgentSessionProvider.CODEX, true)
    providerSettings.setProviderEnabled(AgentSessionProvider.CLAUDE, true)
    providerSettings.setProviderEnabled(AgentSessionProvider.JUNIE, true)
    providerSettings.setProviderEnabled(AgentSessionProvider.PI, true)
    availabilityService.clearAvailabilityForTest()
  }

  @Test
  fun bannerShowsMissingProviderAndCanDisableIt(@TestDisposable disposable: Disposable) {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    providerSettings.setProviderEnabled(AgentSessionProvider.CODEX, true)
    availabilityService.setAvailabilityForTest(mapOf(AgentSessionProvider.CODEX to false))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runInEdtAndWait {
        val banner = AgentProviderCliStatusBanner(project = project, parentDisposable = disposable, refreshSessions = {})

        assertThat(banner.isVisible).isTrue()
        assertThat(banner.componentsOfType(JLabel::class.java).map { it.text })
          .contains(AgentSessionsBundle.message("toolwindow.provider.cli.banner.single.title", "Codex"))
        assertThat(banner.componentsOfType(JBTextArea::class.java).map { it.text })
          .contains(AgentSessionsBundle.message("toolwindow.provider.cli.banner.single.body", "Codex", "Codex"))
        assertThat(banner.componentsOfType(JButton::class.java).map { it.text })
          .containsExactly(
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.disable", "Codex"),
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.settings"),
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.retry"),
          )

        banner.button(AgentSessionsBundle.message("toolwindow.provider.cli.banner.disable", "Codex")).doClick()

        assertThat(providerSettings.isProviderEnabled(AgentSessionProvider.CODEX)).isFalse()
        assertThat(banner.isVisible).isFalse()
      }
    }
  }

  @Test
  fun bannerShowsVisibleDisableButtonForEachMissingProvider(@TestDisposable disposable: Disposable) {
    val codexDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val claudeDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    providerSettings.setProviderEnabled(AgentSessionProvider.CODEX, true)
    providerSettings.setProviderEnabled(AgentSessionProvider.CLAUDE, true)
    availabilityService.setAvailabilityForTest(mapOf(AgentSessionProvider.CODEX to false, AgentSessionProvider.CLAUDE to false))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(codexDescriptor, claudeDescriptor))) {
      runInEdtAndWait {
        val banner = AgentProviderCliStatusBanner(project = project, parentDisposable = disposable, refreshSessions = {})

        assertThat(banner.isVisible).isTrue()
        assertThat(banner.componentsOfType(JLabel::class.java).map { it.text })
          .contains(AgentSessionsBundle.message("toolwindow.provider.cli.banner.multiple.title"))
        assertThat(banner.componentsOfType(JBTextArea::class.java).map { it.text })
          .contains(AgentSessionsBundle.message("toolwindow.provider.cli.banner.multiple.body"))
        val buttons = banner.componentsOfType(JButton::class.java).map { it.text }
        assertThat(buttons.take(2))
          .containsExactlyInAnyOrder(
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.disable", "Codex"),
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.disable", "Claude"),
          )
        assertThat(buttons.drop(2))
          .containsExactly(
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.settings"),
            AgentSessionsBundle.message("toolwindow.provider.cli.banner.retry"),
          )
      }
    }
  }

  @Test
  fun bannerDoesNotShowMissingDiscoverableProvider(@TestDisposable disposable: Disposable) {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.PI,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
    )
    providerSettings.setProviderEnabled(AgentSessionProvider.PI, true)
    availabilityService.setAvailabilityForTest(mapOf(AgentSessionProvider.PI to false))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runInEdtAndWait {
        val banner = AgentProviderCliStatusBanner(project = project, parentDisposable = disposable, refreshSessions = {})

        assertThat(banner.isVisible).isFalse()
      }
    }
  }

  private fun JComponent.button(text: String): JButton {
    return componentsOfType(JButton::class.java).single { it.text == text }
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
}
