// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import javax.swing.JComponent

private class AgentSessionProviderUiContributorRegistryLog

private val UI_CONTRIBUTOR_LOG = logger<AgentSessionProviderUiContributorRegistryLog>()

interface AgentSessionProviderUiContributor {
  fun onConversationOpened() {
  }

  fun createToolWindowNorthComponent(project: Project): JComponent? = null
}

class AgentSessionProviderUiContributorBean : BaseKeyedLazyInstance<AgentSessionProviderUiContributor>() {
  @Attribute("providerId")
  @JvmField
  @RequiredElement
  var providerId: String = ""

  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  override fun getImplementationClassName(): String = implementation

  fun matches(provider: AgentSessionProvider): Boolean {
    val contributedProvider = AgentSessionProvider.fromOrNull(providerId)
    if (contributedProvider == null) {
      UI_CONTRIBUTOR_LOG.warn("Ignoring session provider UI contributor with invalid providerId '$providerId': $implementation")
      return false
    }
    return contributedProvider == provider
  }
}

object AgentSessionProviderUiContributors {
  val EP_NAME: ExtensionPointName<AgentSessionProviderUiContributorBean> =
    ExtensionPointName("com.intellij.agent.workbench.sessionProviderUiContributor")

  fun forProvider(provider: AgentSessionProvider): List<AgentSessionProviderUiContributor> {
    return EP_NAME.extensionList.mapNotNull { contributorBean ->
      if (contributorBean.matches(provider)) contributorBean.instance else null
    }
  }
}
