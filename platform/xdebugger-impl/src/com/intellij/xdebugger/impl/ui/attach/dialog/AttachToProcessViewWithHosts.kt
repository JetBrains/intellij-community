// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

abstract class AttachToProcessViewWithHosts(
  project: Project,
  state: AttachDialogState,
  columnsLayout: AttachDialogColumnsLayout,
  attachDebuggerProviders: List<XAttachDebuggerProvider>
) : AttachToProcessView(project, state, columnsLayout, attachDebuggerProviders) {
  companion object{
    // used externally
    @Suppress("MemberVisibilityCanBePrivate")
    val DEFAULT_ATTACH_HOST: DataKey<String> = DataKey.create("DEFAULT_ATTACH_HOST")
    private fun getDefaultAttachHost(state: AttachDialogState) = state.dataContext.getData(DEFAULT_ATTACH_HOST)
  }

  protected abstract val addHostButtonAction: AddConnectionButtonAction
  protected abstract val hostsComboBoxAction: AttachHostsComboBoxAction

  abstract fun openSettings()
  abstract fun openSettingsAndCreateTemplate()
  abstract fun isAddingConnectionEnabled(): Boolean
  abstract fun saveSelectedHost(host: AttachHostItem?)
  abstract fun getSavedHost(allHosts: Set<AttachHostItem>): AttachHostItem?
  protected open fun getHostFromSet(allHosts: Set<AttachHostItem>) = allHosts.firstOrNull()
  protected open fun handleSingleHost(allHosts: Set<AttachHostItem>, selectedHost: AttachHostItem, addedHost: AttachHostItem) = addedHost
  protected open fun handleMultipleHosts(allHosts: Set<AttachHostItem>, selectedHost: AttachHostItem) =
    if (allHosts.contains(selectedHost)) selectedHost
    else allHosts.firstOrNull()
  abstract fun getHostActions(hosts: Set<AttachHostItem>, selectHost: (host: AttachHostItem) -> Unit): List<AnAction>
  abstract suspend fun getHosts(): List<AttachHostItem>

  protected fun hostsSettingsChanged() {
    updateProcesses()
  }

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  protected suspend fun updateHosts(): List<AttachHostItem> {
    val hosts = getHosts()

    withUiContextAnyModality {
      addHostButtonAction.updateState(hosts)
      hostsComboBoxAction.updateState(hosts)
    }

    if (hosts.isEmpty()) {
      withUiContextAnyModality {
        val component = if (!isAddingConnectionEnabled()) {
          JPanel(MigLayout("ins 0, gap 0, fill")).apply {
            add(JLabel(XDebuggerBundle.message("xdebugger.no.remote.connections.message")), "align center")
          }
        }
        else {
          getActionablePane(
            XDebuggerBundle.message("xdebugger.no.remote.connections.message"),
            XDebuggerBundle.message("xdebugger.attach.add.connection.message")) {
            openSettingsAndCreateTemplate()
          }
        }
        loadComponent(component)
      }
    }

    return hosts
  }

  override suspend fun doUpdateProcesses() {
    val hosts = updateHosts()
    if (hosts.isEmpty()) return

    withContext(Dispatchers.IO) {
      val hostItem = hostsComboBoxAction.getSelectedItem()
      collectAndShowItems(hostItem.host)
    }
  }

  override fun getViewActions(): List<AnAction> = listOf(hostsComboBoxAction, addHostButtonAction)

  private fun getActionablePane(@Nls description: String, @Nls actionName: String, action: () -> Unit): JComponent {
    val component = JPanel(MigLayout("ins 0, gap 0, fill"))
    component.add(JLabel(description), "alignx center, aligny bottom, wrap")
    component.add(ActionLink(actionName) { action() }, "alignx center, aligny top")
    component.background = JBUI.CurrentTheme.List.BACKGROUND
    return component
  }

  protected inner class AttachHostsComboBoxAction(private val showDisabledActions: Boolean = false) : ComboBoxAction(), DumbAware {
    init {
      isSmallVariant = false
    }

    override fun shouldShowDisabledActions() = showDisabledActions

    private var hosts: Set<AttachHostItem> = emptySet()

    private var selectedHost: AttachHostItem? = null
      set(value) {
        saveSelectedHost(value)
        field = value
      }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
      val actions = DefaultActionGroup()

      actions.addAll(getHostActions(hosts) { host -> selectedHost = host })

      if (isAddingConnectionEnabled()) {
        actions.add(ManageConnectionsAction())
      }

      return actions
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = selectedHost?.getIcon()
      e.presentation.text = StringUtil.trimMiddle(selectedHost?.getPresentation() ?: "", 30)
      e.presentation.isEnabledAndVisible = hosts.any()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun getSelectedItem(): AttachHostItem = selectedHost ?: throw IllegalStateException("At least one host should be selected")

    fun updateState(newHosts: List<AttachHostItem>): Boolean {
      ThreadingAssertions.assertEventDispatchThread()

      val newHostsAsSet = newHosts.toSet()
      val addedHosts = newHostsAsSet.filter { !hosts.contains(it) }
      val removedHosts = hosts.filter { !newHostsAsSet.contains(it) }
      val selected = selectedHost

      val newSelectedHost =
        if (selected == null) {
          val defaultAttachHost = getDefaultAttachHost(state)
          val defaultAttachHostItem = if (defaultAttachHost != null) newHostsAsSet.find { it.getId() == defaultAttachHost } else null
          defaultAttachHostItem ?: getSavedHost(newHostsAsSet) ?: getHostFromSet(newHostsAsSet)
        }
        else if (addedHosts.size == 1 && removedHosts.size <= 1) { //new connection was added (or modified)
          handleSingleHost(newHostsAsSet, selected, addedHosts.single())
        }
        else {
          handleMultipleHosts(newHostsAsSet, selected)
        }

      hosts = newHostsAsSet
      selectedHost = newSelectedHost

      return addedHosts.isNotEmpty() || removedHosts.isNotEmpty()
    }
  }

  protected inner class AddConnectionButtonAction :
    AnAction(XDebuggerBundle.message("xdebugger.attach.add.connection.message")), CustomComponentAction, ActionListener, DumbAware {

    private var isEnabled = false

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isAddingConnectionEnabled() && isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      openSettingsAndCreateTemplate()
    }

    override fun actionPerformed(e: ActionEvent?) {
      openSettingsAndCreateTemplate()
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return JButton(presentation.text).apply {
        addActionListener(this@AddConnectionButtonAction)
      }
    }

    fun updateState(hosts: List<AttachHostItem>): Boolean {
      val previousValue = isEnabled
      isEnabled = hosts.isEmpty()
      return previousValue xor isEnabled
    }
  }

  protected inner class ManageConnectionsAction :
    AnAction(XDebuggerBundle.message("xdebugger.attach.manage.connections.message")), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      openSettings()
    }
  }
}