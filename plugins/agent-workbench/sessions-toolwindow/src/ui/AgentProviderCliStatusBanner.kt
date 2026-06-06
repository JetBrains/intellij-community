// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.service.AgentArchivedSessionsService
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityListener
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsListener
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.settings.AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<AgentProviderCliStatusBanner>()

private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"

internal class AgentProviderCliStatusBanner(
  private val project: Project,
  parentDisposable: Disposable,
  private val providerAvailabilityService: AgentSessionProviderAvailabilityService =
    project.service(),
  private val providerSettingsService: AgentSessionProviderSettingsService = service(),
  private val refreshSessions: () -> Unit = {
    service<AgentSessionRefreshService>().refresh()
    service<AgentArchivedSessionsService>().refreshIfLoaded()
  },
) : JPanel(BorderLayout(0, JBUI.scale(8))) {

  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1),
      JBUI.Borders.empty(8),
    )
    isVisible = false

    project.messageBus.connect(parentDisposable)
      .subscribe(AgentSessionProviderAvailabilityListener.TOPIC, object : AgentSessionProviderAvailabilityListener {
        override fun availabilityChanged() {
          syncPresentation()
        }
      })
    ApplicationManager.getApplication().messageBus.connect(parentDisposable)
      .subscribe(AgentSessionProviderSettingsListener.TOPIC, object : AgentSessionProviderSettingsListener {
        override fun providerSettingsChanged() {
          syncPresentation()
        }
      })
    syncPresentation()
  }

  private fun syncPresentation() {
    val missingProviders = missingEnabledProviders()
    removeAll()
    isVisible = missingProviders.isNotEmpty()
    if (missingProviders.isNotEmpty()) {
      add(textPanel(missingProviders), BorderLayout.CENTER)
      add(actionsPanel(missingProviders), BorderLayout.SOUTH)
    }
    revalidate()
    repaint()
  }

  private fun missingEnabledProviders(): List<AgentSessionProviderDescriptor> {
    val providers = providerSettingsService.enabledProviders(AgentSessionProviders.allProviders())
    val availability = providerAvailabilityService.availabilitySnapshot(providers)
    return providers.filter { provider ->
      provider.cliVisibilityPolicy == AgentSessionProviderCliVisibilityPolicy.PROMINENT && availability[provider.provider] == false
    }
  }

  private fun textPanel(missingProviders: List<AgentSessionProviderDescriptor>): JPanel {
    val title = if (missingProviders.size == 1) {
      AgentSessionsBundle.message("toolwindow.provider.cli.banner.single.title", providerCliDisplayName(missingProviders.single()))
    }
    else {
      AgentSessionsBundle.message("toolwindow.provider.cli.banner.multiple.title")
    }
    val message = if (missingProviders.size == 1) {
      val provider = missingProviders.single()
      AgentSessionsBundle.message("toolwindow.provider.cli.banner.single.body",
                                  providerCliDisplayName(provider),
                                  providerDisplayName(provider))
    }
    else {
      AgentSessionsBundle.message("toolwindow.provider.cli.banner.multiple.body")
    }

    return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
      isOpaque = false
      add(JLabel(title).apply {
        font = font.deriveFont(font.style or Font.BOLD)
      }, BorderLayout.NORTH)
      add(JBTextArea(message).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = null
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getLabelForeground()
      }, BorderLayout.CENTER)
    }
  }

  private fun actionsPanel(missingProviders: List<AgentSessionProviderDescriptor>): JPanel {
    return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      missingProviders.forEach { provider ->
        add(JButton(AgentSessionsBundle.message("toolwindow.provider.cli.banner.disable", providerDisplayName(provider))).apply {
          addActionListener {
            disableProvider(provider)
          }
        })
      }
      add(JButton(AgentSessionsBundle.message("toolwindow.provider.cli.banner.settings")).apply {
        addActionListener {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID)
        }
      })
      add(JButton(AgentSessionsBundle.message("toolwindow.provider.cli.banner.retry")).apply {
        addActionListener {
          retryProviderAvailability()
        }
      })
    }
  }

  private fun retryProviderAvailability() {
    providerAvailabilityService.requestRefresh(providerSettingsService.enabledProviders(AgentSessionProviders.allProviders()), force = true)
    refreshSessions()
  }

  private fun disableProvider(provider: AgentSessionProviderDescriptor) {
    providerSettingsService.setProviderEnabled(provider.provider, false)
    refreshSessions()
    showProviderDisabledNotification(provider)
  }

  private fun showProviderDisabledNotification(provider: AgentSessionProviderDescriptor) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    runCatching {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(AGENT_SESSIONS_NOTIFICATION_GROUP_ID)
        .createNotification(
          AgentSessionsBundle.message("toolwindow.provider.notification.disabled.title", providerDisplayName(provider)),
          AgentSessionsBundle.message("toolwindow.provider.notification.disabled.body", providerDisplayName(provider)),
          NotificationType.INFORMATION,
        )
        .addAction(
          NotificationAction.createSimpleExpiring(
            AgentSessionsBundle.message("toolwindow.provider.notification.disabled.settings"),
            Runnable {
              ShowSettingsUtil.getInstance().showSettingsDialog(project, AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID)
            },
          )
        )
        .notify(project)
    }.onFailure { error ->
      LOG.warn("Failed to show Agent Workbench provider disabled notification", error)
    }
  }

  private fun providerDisplayName(provider: AgentSessionProviderDescriptor): String {
    return runCatching { AgentSessionsBundle.message(provider.displayNameKey) }.getOrDefault(provider.displayNameFallback)
  }

  private fun providerCliDisplayName(provider: AgentSessionProviderDescriptor): String {
    return runCatching { AgentSessionsBundle.message(provider.cliDisplayNameKey) }.getOrDefault(provider.cliDisplayNameFallback)
  }
}
