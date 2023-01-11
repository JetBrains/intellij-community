package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachHostProvider
import com.intellij.xdebugger.impl.actions.AttachToProcessAction
import com.intellij.xdebugger.impl.ui.attach.dialog.extensions.XAttachHostSettingsProvider
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

internal class AttachToRemoteProcessView(private val project: Project,
                                state: AttachDialogState,
                                private val attachHostProviders: List<XAttachHostProvider<out XAttachHost>>,
                                debuggerProviders: List<XAttachDebuggerProvider>) : AttachToProcessView(project, state, debuggerProviders) {

  companion object {
    private val logger = Logger.getInstance(AttachToProcessAction::class.java)
    private const val SELECTED_HOST_KEY = "ATTACH_DIALOG_SELECTED_HOST"
  }

  private val addHostButtonAction = AddConnectionButtonAction()
  private val hostsComboBoxAction = AttachHostsComboBoxAction()

  private val attachHostSettingsProviders = XAttachHostSettingsProvider.EP.extensionList

  private val propertiesComponent = PropertiesComponent.getInstance(project)

  init {
    attachHostSettingsProviders.forEach {
      it.addSettingsChangedListener(project, state.dialogDisposable) {
        hostsSettingsChanged()
      }
    }
  }

  override suspend fun doUpdateProcesses() {
    val hosts = getHosts()

    withUiContextAnyModality {
      addHostButtonAction.updateState(hosts)
      hostsComboBoxAction.updateState(hosts)
    }

    if (hosts.isEmpty()) {
      withUiContextAnyModality {
        val component = if (attachHostSettingsProviders.isEmpty()) {
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

      return
    }

    withContext(Dispatchers.IO) {
      val hostAndProvider = hostsComboBoxAction.getSelectedItem()
      collectAndShowItems(hostAndProvider.host)
    }
  }

  override fun getHostType(): AttachDialogHostType = AttachDialogHostType.REMOTE

  override fun getViewActions(): List<AnAction> = listOf(hostsComboBoxAction, addHostButtonAction)

  private fun hostsSettingsChanged() {
    updateProcesses()
  }

  private fun getHosts(): List<AttachHostAndProvider> {
    val dataHolder = UserDataHolderBase()
    return attachHostProviders.flatMap { provider ->
      provider.getAvailableHosts(project).map { AttachHostAndProvider(it, provider, project, dataHolder) }
    }
  }

  private fun openSettings() {
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

  private fun openSettingsAndCreateTemplate() {
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

  private fun getActionablePane(@Nls description: String, @Nls actionName: String, action: () -> Unit): JComponent {
    val component = JPanel(MigLayout("ins 0, gap 0, fill"))
    component.add(JLabel(description), "alignx center, aligny bottom, wrap")
    component.add(ActionLink(actionName) { action() }, "alignx center, aligny top")
    component.background = JBUI.CurrentTheme.List.BACKGROUND
    return component
  }

  private fun saveSelectedHost(host: AttachHostAndProvider?) {
    val hostPresentationKey = host?.toString()
    propertiesComponent.setValue(SELECTED_HOST_KEY, hostPresentationKey)
  }

  private fun getSavedHost(allHosts: Set<AttachHostAndProvider>): AttachHostAndProvider? {
    val savedValue = propertiesComponent.getValue(SELECTED_HOST_KEY) ?: return null
    return allHosts.firstOrNull { it.toString() == savedValue }
  }

  private inner class AttachHostsComboBoxAction : ComboBoxAction(), DumbAware {

    private var mySelectedHost: AttachHostAndProvider? = null

    private var selectedHost: AttachHostAndProvider?
      get() = mySelectedHost
      set(value) {
        saveSelectedHost(value)
        mySelectedHost = value
      }
    private var hosts: Set<AttachHostAndProvider> = emptySet()

    init {
      isSmallVariant = false
    }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {

      val actions = DefaultActionGroup()

      for (providerAndHosts in hosts.groupBy { it.provider }.toList().sortedBy { it.first.presentationGroup.order }) {
        for (host in providerAndHosts.second) {
          actions.add(object : AnAction({ host.toString() }, host.getIcon()) {
            override fun actionPerformed(e: AnActionEvent) {
              selectedHost = host
              updateProcesses()
            }
          })
        }
        actions.add(Separator())
      }

      if (attachHostSettingsProviders.any()) {
        actions.add(ManageConnectionsAction())
      }
      return actions
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = selectedHost?.getIcon()
      e.presentation.text = StringUtil.trimMiddle(selectedHost?.toString() ?: "", 30)
      e.presentation.isEnabledAndVisible = hosts.any()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun getSelectedItem(): AttachHostAndProvider = selectedHost ?: throw IllegalStateException("At least one host should be selected")
    fun updateState(newHosts: List<AttachHostAndProvider>): Boolean {
      application.assertIsDispatchThread()

      val newHostsAsSet = newHosts.toSet()
      val addedHosts = newHostsAsSet.filter { !hosts.contains(it) }
      val removedHosts = hosts.filter { !newHostsAsSet.contains(it) }

      val newSelectedHost =
        if (selectedHost == null) {
          getSavedHost(newHostsAsSet) ?: newHostsAsSet.firstOrNull()
        }
        else if (addedHosts.size == 1 && removedHosts.size <= 1) { //new connection was added (or modified)
          addedHosts.single()
        }
        else {
          if (newHostsAsSet.contains(selectedHost)) {
            selectedHost
          }
          else {
            newHostsAsSet.firstOrNull()
          }
        }

      hosts = newHostsAsSet
      selectedHost = newSelectedHost

      return addedHosts.isNotEmpty() || removedHosts.isNotEmpty()
    }
  }

  private inner class AddConnectionButtonAction :
    AnAction(XDebuggerBundle.message("xdebugger.attach.add.connection.message")), CustomComponentAction, ActionListener, DumbAware {

    private var isEnabled = false

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return JButton(presentation.text).apply {
        addActionListener(this@AddConnectionButtonAction)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = attachHostSettingsProviders.any() && isEnabled
    }

    override fun actionPerformed(e: ActionEvent?) {
      openSettingsAndCreateTemplate()
    }

    override fun actionPerformed(e: AnActionEvent) {
      openSettingsAndCreateTemplate()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    fun updateState(hosts: List<AttachHostAndProvider>): Boolean {
      val previousValue = isEnabled
      isEnabled = hosts.isEmpty()
      return previousValue xor isEnabled
    }
  }

  private inner class ManageConnectionsAction : AnAction(XDebuggerBundle.message("xdebugger.attach.manage.connections.message")), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      openSettings()
    }
  }
}