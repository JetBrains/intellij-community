// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.actions.AttachToProcessAction
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachHostSettingsProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class AttachToRemoteProcessView(private val project: Project,
                                         state: AttachDialogState,
                                         columnsLayout: AttachDialogColumnsLayout,
                                         private val attachHostProviders: List<XAttachHostProvider<out XAttachHost>>,
                                         debuggerProviders: List<XAttachDebuggerProvider>) : AttachToProcessViewWithHosts(project, state,
                                                                                                                          columnsLayout,
                                                                                                                          debuggerProviders) {

  companion object {
    private val logger = Logger.getInstance(AttachToProcessAction::class.java)
    private const val SELECTED_HOST_KEY = "ATTACH_DIALOG_SELECTED_HOST"
  }

  override val addHostButtonAction = AddConnectionButtonAction()
  override val hostsComboBoxAction = AttachHostsComboBoxAction()

  private val attachHostSettingsProviders = XAttachHostSettingsProvider.EP.extensionList

  private val propertiesComponent = PropertiesComponent.getInstance(project)

  init {
    attachHostSettingsProviders.forEach {
      it.addSettingsChangedListener(project, state.dialogDisposable) {
        hostsSettingsChanged()
      }
    }
  }

  override fun getHostType(): AttachDialogHostType = AttachDialogHostType.REMOTE

  override suspend fun getHosts(): List<AttachHostAndProvider> {
    val dataHolder = UserDataHolderBase()
    return coroutineScope {
      attachHostProviders.map { provider ->
        async { provider.getAvailableHostsAsync(project).map { AttachHostAndProvider(it, provider, project, dataHolder) } }
      }.awaitAll()
    }.flatten()
  }

  override fun openSettings() {
    if (attachHostSettingsProviders.isEmpty()) {
      throw IllegalStateException(
        "There's should be at least one settings provider on this step")
    }
    if (attachHostSettingsProviders.size == 1) {
      attachHostSettingsProviders.first().openHostsSettings(project)
      return
    }
    logger.error("The ability to choose the settings to be opened is not implemented yet")
    attachHostSettingsProviders.first().openHostsSettings(project)
  }

  override fun openSettingsAndCreateTemplate() {
    if (attachHostSettingsProviders.isEmpty()) {
      throw IllegalStateException(
        "There's should be at least one settings provider on this step")
    }
    if (attachHostSettingsProviders.size == 1) {
      attachHostSettingsProviders.first().openAndCreateTemplate(project)
      return
    }
    logger.error("The ability to choose the settings to be opened is not implemented yet")
    attachHostSettingsProviders.first().openAndCreateTemplate(project)
  }

  override fun isAddingConnectionEnabled() = attachHostSettingsProviders.any()

  override fun saveSelectedHost(host: AttachHostItem?) {
    val hostPresentationKey = host?.toString()
    propertiesComponent.setValue(SELECTED_HOST_KEY, hostPresentationKey)
  }

  override fun getSavedHost(allHosts: Set<AttachHostItem>): AttachHostItem? {
    val savedValue = propertiesComponent.getValue(SELECTED_HOST_KEY) ?: return null
    return allHosts.firstOrNull { it.toString() == savedValue }
  }

  override fun getHostActions(hosts: Set<AttachHostItem>, selectHost: (host: AttachHostItem) -> Unit): List<AnAction> {
    val actions = mutableListOf<AnAction>()

    for (providerAndHosts in hosts.filterIsInstance<AttachHostAndProvider>().groupBy { it.provider }.toList().sortedBy { it.first.getPresentationGroup().order }) {
      for (host in providerAndHosts.second) {
        actions.add(object : AnAction({ host.getPresentation() }, host.getIcon()) {
          override fun actionPerformed(e: AnActionEvent) {
            selectHost(host)
            updateProcesses()
          }
        })
      }
      actions.add(Separator())
    }

    return actions
  }
}