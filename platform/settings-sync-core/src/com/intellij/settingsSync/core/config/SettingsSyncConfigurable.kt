@file:OptIn(IntellijInternalApi::class)

package com.intellij.settingsSync.core.config


import com.intellij.BundleBase
import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.settingsSync.core.DeleteServerDataResult
import com.intellij.settingsSync.core.SettingsSyncBridge
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncLocalStateHolder
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.intellij.settingsSync.core.UpdateResult.Error
import com.intellij.settingsSync.core.UpdateResult.FileDeletedFromServer
import com.intellij.settingsSync.core.UpdateResult.NoFileOnServer
import com.intellij.settingsSync.core.UpdateResult.Success
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService.PendingUserAction
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.core.communicator.getAvailableSyncProviders
import com.intellij.settingsSync.core.config.SettingsSyncEnabler.State
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.whenItemSelectedFromUi
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.util.Consumer
import com.intellij.util.IconUtil
import com.intellij.util.asDisposable
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CancellationException
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border

internal class SettingsSyncConfigurable(private val coroutineScope: CoroutineScope) : BoundConfigurable(message("title.settings.sync")),
                                                                                      SettingsSyncEnabler.Listener,
                                                                                      SettingsSyncStatusTracker.Listener,
                                                                                      SettingsSyncEventListener {
  companion object {
    private val LOG = logger<SettingsSyncConfigurable>()
  }

  private lateinit var configPanel: DialogPanel
  private lateinit var enableCheckbox: JCheckBox

  private val userAccountsList = arrayListOf<UserProviderHolder>()
  private var userAccountsLogout: UserProviderHolder? = null
  private val userComboBoxModel = MutableCollectionComboBoxModel<UserProviderHolder>()
  private var userProviderHolder: UserProviderHolder? = currentUser()
  private lateinit var cellUserComboBox: Cell<ComboBox<UserProviderHolder>>

  private lateinit var syncTypeBanner: InlineBanner
  private lateinit var syncConfigPanel: DialogPanel

  private val syncEnabler = SettingsSyncEnabler()
  private val enableSyncOption = AtomicProperty<InitSyncType>(InitSyncType.GET_FROM_SERVER)
  private val disableSyncOption = AtomicProperty<DisableSyncType>(DisableSyncType.DISABLE)
  private val remoteSettingsExist = AtomicBooleanProperty(false)
  private val showRemoveDataErrorPanel = AtomicBooleanProperty(false)
  private val userAccountChangeEnabled = AtomicBooleanProperty(true)
  private val wasUsedBefore = AtomicBooleanProperty(currentUser() != null)
  private val userAccountListIsNotEmpty = AtomicBooleanProperty(false)
  private val syncPanelHolder = SettingsSyncPanelHolder()
  private val hasMultipleProviders = AtomicBooleanProperty(RemoteCommunicatorHolder.getExternalProviders().isNotEmpty())

  private val actionRequired = AtomicBooleanProperty(false)
  private lateinit var actionRequiredLabel: JLabel
  private lateinit var actionRequiredButton: JButton
  private var actionRequiredAction: (suspend() -> Unit)? = null


  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
    SettingsSyncEvents.getInstance().addListener(this)
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    SettingsSyncStatusTracker.getInstance().removeListener(this)
    SettingsSyncEvents.getInstance().removeListener(this)
  }

  private fun currentUserId() = SettingsSyncLocalSettings.getInstance().userId

  private fun currentUser(): UserProviderHolder? {
    val userId = currentUserId() ?: return null
    val providerCode = SettingsSyncLocalSettings.getInstance().providerCode ?: return null
    val authService = RemoteCommunicatorHolder.getProvider(providerCode)?.authService ?: return null
    return authService.getAvailableUserAccounts().find {
      it.id == userId
    }?.toUserProviderHolder(authService.providerName)
  }

  private fun selectedUser(): UserProviderHolder? {
    return userComboBoxModel.selectedItem as? UserProviderHolder
  }

  override fun createPanel(): DialogPanel {
    syncConfigPanel = syncPanelHolder.createCombinedSyncSettingsPanel(message("configurable.what.to.sync.label"),
                                                                      SettingsSyncSettings.getInstance(),
                                                                      SettingsSyncLocalSettings.getInstance())

    configPanel = panel {
      updateUserAccountsList()
      validateCurrentUser()
      updateRemoveDataState(RemoteDataRemovalState.fromString(SettingsSyncLocalSettings.getInstance().remoteDataRemovalState))
      updateUserAccountLogout(userProviderHolder)
      userComboBoxModel.selectedItem = userProviderHolder
      updateUserComboBoxModel()
      val authService = currentUser()?.let { RemoteCommunicatorHolder.getProvider(it.providerCode) } ?.authService
      syncPanelHolder.crossSyncSupported.set(authService?.crossSyncSupported() ?: true)
      val infoRow = row {
        @Suppress("DialogTitleCapitalization")
        text(message("settings.sync.info.message"))
        getAvailableSyncProviders()
          .firstOrNull { it.learnMoreLinkPair != null }
          ?.also {
            val linkPair = it.learnMoreLinkPair!!
            @Suppress("HardCodedStringLiteral")
            browserLink(linkPair.first, linkPair.second)
          }
      }

      rowsRange {
        row {
          text(message("settings.sync.select.provider.message"))
        }.visibleIf(hasMultipleProviders)

        row {
          val availableProviders = RemoteCommunicatorHolder.getAvailableProviders()
          availableProviders.forEachIndexed { idx, provider ->
            if (idx > 0) {
              @Suppress("DialogTitleCapitalization")
              label(message("settings.sync.select.provider.or")).gap(RightGap.SMALL)
            }
            button(provider.authService.providerName) {
              login(provider, syncConfigPanel)
            }.applyToComponent {
              icon = provider.authService.icon
            }.gap(RightGap.SMALL)
          }
        }.visibleIf(hasMultipleProviders)

        row {
          val defaultProvider = RemoteCommunicatorHolder.getDefaultProvider() ?: return@row
          button(message("config.button.login")) {
            login(defaultProvider, syncConfigPanel)
          }
        }.visibleIf(hasMultipleProviders.not())
      }.visibleIf(userAccountListIsNotEmpty.not())

      row {
        val enableCheckboxCell = checkBox(message("config.button.enable")).applyToComponent {
          iconTextGap = 6
          isSelected = SettingsSyncSettings.getInstance().syncEnabled
        }.enabledIf(userAccountChangeEnabled).gap(RightGap.SMALL)
        enableCheckbox = enableCheckboxCell.component
        enableCheckbox.addActionListener {
          enableButtonAction()
        }
        infoRow.visibleIf(enableCheckbox.selected.not())

        val listCellRenderer = listCellRenderer<UserProviderHolder>("") {
          val holder = value
          var icon2Apply = IconUtil.getEmptyIcon(false)
          when {
            holder.userId == UserProviderHolder.LOGOUT_USER_ID -> {
              separator { text = "" }
              text(message("logout.link.text", holder.providerName)) {
                attributes = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES.derive(
                  SimpleTextAttributes.STYLE_PLAIN,
                  JBUI.CurrentTheme.Link.Foreground.ENABLED,
                  null,
                  null
                )
                font = JBFont.medium()
              }
            }
            holder == UserProviderHolder.ADD_ACCOUNT -> {
              icon(icon2Apply)
              separator { text = "" }
              @Suppress("HardCodedStringLiteral")
              text(holder.toString())
            }
            else -> {
              if (index == -1) {
                RemoteCommunicatorHolder.getProvider(holder.providerCode)?.authService?.icon?.let {
                  icon2Apply = it
                }
              }
              else {
                if (index == 0 || holder.providerCode != userAccountsList[index - 1].providerCode) {
                  separator { text = holder.providerName }
                }
              }
              if (index >= 0 && holder == userComboBoxModel.selectedItem) {
                icon2Apply = AllIcons.Actions.Checked
              }
              icon(icon2Apply)
              @Suppress("HardCodedStringLiteral")
              text(holder.toString())
            }
          }
        }

        cellUserComboBox = comboBox(userComboBoxModel, listCellRenderer)
          .enabledIf(userAccountChangeEnabled)
          .resizableColumn().align(AlignX.FILL)
          .comment("", 50)
        cellUserComboBox.whenItemSelectedFromUi { item ->
          if (item != userProviderHolder) {
            tryChangeAccount(item)
          }
        }

      }.visibleIf(userAccountListIsNotEmpty)

      // settings to sync
      rowsRange {
        @Suppress("DialogTitleCapitalization")
        group(message("enable.dialog.select.what.to.sync")) {
          row {
            syncTypeBanner = InlineBanner("", EditorNotificationPanel.Status.Warning).apply {
              showCloseButton(false)
              addAction(message("enable.dialog.change")) {
                val syncTypeDialog = ChangeSyncTypeDialog(configPanel, enableSyncOption.get())
                if (syncTypeDialog.showAndGet()) {
                  enableSyncOption.set(syncTypeDialog.option)
                }
              }
              enableSyncOption.afterChange {
                updateSyncTypeBannerText()
              }
            }
            cell(syncTypeBanner).resizableColumn().align(AlignX.FILL).customize(UnscaledGaps(8))
          }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL).visibleIf(remoteSettingsExist)

          row {
            cell(syncConfigPanel)
              .onReset {
                syncConfigPanel.reset()
                wasUsedBefore.set(currentUser() != null)
                hasMultipleProviders.set(RemoteCommunicatorHolder.getExternalProviders().isNotEmpty())
                enableCheckbox.isSelected = SettingsSyncSettings.getInstance().syncEnabled
                userProviderHolder = if (currentUser() != null) {
                  userAccountsList.firstOrNull { it.userId == currentUserId() }
                } else if (userAccountListIsNotEmpty.get()) {
                  userAccountsList.firstOrNull { it != UserProviderHolder.ADD_ACCOUNT }
                } else {
                  null
                }
                userComboBoxModel.selectedItem = userProviderHolder
                updateUserAccountLogout(userProviderHolder)
                updateUserComboBoxModel()
                syncStatusChanged()
              }
              .onIsModified {
                enableCheckbox.isSelected != SettingsSyncSettings.getInstance().syncEnabled
                || syncConfigPanel.isModified()
                || (currentUserId() != null && selectedUser()?.userId != currentUserId())
              }
              .onApply {
                val selectedUser = selectedUser()
                with(SettingsSyncLocalSettings.getInstance()) {
                  userId = selectedUser?.userId
                  providerCode = selectedUser?.providerCode
                }
                cellUserComboBox.comment?.text = ""
                if (enableCheckbox.isSelected) {
                  syncConfigPanel.apply()
                }
                if (SettingsSyncSettings.getInstance().syncEnabled != enableCheckbox.isSelected) {
                  if (enableCheckbox.isSelected) {
                    SettingsSyncSettings.getInstance().syncEnabled = true
                    if (enableSyncOption.get() == InitSyncType.GET_FROM_SERVER) {
                      syncEnabler.getSettingsFromServer()
                    }
                    else {
                      syncEnabler.pushSettingsToServer()
                    }
                  }
                  else {
                    handleDisableSync()
                  }
                  // clear the flag
                  remoteSettingsExist.set(false)
                }
              }
          }.topGap(TopGap.SMALL)
        }.visibleIf(enableCheckbox.selected.and(ComponentPredicate.fromObservableProperty(actionRequired.not())))

        rowsRange {
          row {
            label("").applyToComponent {
              icon = AllIcons.General.Error
              foreground = NamedColorUtil.getErrorForeground()
              actionRequiredLabel = this
            }
          }
          row {
            button("") {
              coroutineScope.launch(ModalityState.current().asContextElement()) {
                withContext(Dispatchers.EDT) {
                  actionRequiredAction?.invoke()
                }
              }
            }.also {
              actionRequiredButton = it.component
            }
          }
        }.visibleIf(actionRequired)

        row {
          val errorBanner = InlineBanner(
            message("sync.remove.data.error.message.title"),
            EditorNotificationPanel.Status.Error
          ).apply {
            showCloseButton(true)
            setCloseAction { updateRemoveDataState(RemoteDataRemovalState.OK) }

            addAction(message("sync.remove.data.error.message.action.delete")) {
              selectedUser()?.let { repeatedDisableAndRemoveData(it) }
            }

            val contactSupportFunction = currentUser()?.providerCode?.let {
              RemoteCommunicatorHolder.getProvider(it)?.authService?.contactSupportFunction
            }
            if (contactSupportFunction != null) {
              addAction(message("sync.remove.data.error.message.action.support")) {
                contactSupportFunction()
              }
            }
          }
          cell(errorBanner).resizableColumn().align(AlignX.FILL).customize(UnscaledGaps(8))
        }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL).visibleIf(showRemoveDataErrorPanel)

      }.visibleIf(userAccountListIsNotEmpty)

      // apply necessary changes
    }
    syncStatusChanged()
    return configPanel
  }

  private fun handleDisableSync() {
    when (disableSyncOption.get()) {
      DisableSyncType.DISABLE_AND_REMOVE_DATA -> {
        currentUser()?.let {
          disableAndRemoveData(it, true)
          SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(
            SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_AND_REMOVED_DATA_FROM_SERVER)
        }
      }
      DisableSyncType.DISABLE -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
      else -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
    }
    SettingsSyncSettings.getInstance().syncEnabled = false
    disableSyncOption.set(DisableSyncType.DISABLE)
    syncStatusChanged()
  }

  private fun enableButtonAction(){
    // enableCheckbox state here has already changed, so we react to it
    if (enableCheckbox.isSelected) {
      updateRemoveDataState(RemoteDataRemovalState.OK)
      val pendingUserAction = getPendingUserAction()
      if (pendingUserAction != null) {
        refreshActionRequired()
        return
      }
      runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("enable.sync.check.server.data.progress")) {
        val (userId, _, providerCode, providerName) = selectedUser() ?: run {
          LOG.warn("No selected user")
          showErrorOnEDT(message("enable.dialog.error.no.user"))
          enableCheckbox.isSelected = false
          return@runWithModalProgressBlocking
        }
        val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: run {
          LOG.warn("Provider '$providerName' ($providerCode) is not available")
          showErrorOnEDT(message("enable.dialog.error.no.provider", providerName))
          enableCheckbox.isSelected = false
          return@runWithModalProgressBlocking
        }
        val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userId, this@runWithModalProgressBlocking.asDisposable()) ?: run {
          LOG.warn("Cannot create remote communicator of type '$providerName' ($providerCode)")
          showErrorOnEDT(message("enable.dialog.error.cant.check", providerName))
          enableCheckbox.isSelected = false
          return@runWithModalProgressBlocking
        }
        if (checkServerState(syncPanelHolder, remoteCommunicator, provider.authService.crossSyncSupported())) {
          withContext(Dispatchers.EDT) {
            syncConfigPanel.reset()
            triggerUpdateConfigurable()
          }
          cellUserComboBox.comment?.text = message("sync.status.will.enable",
                                                   CommonBundle.getApplyButtonText().replace(BundleBase.MNEMONIC_STRING, ""))
        } else {
          enableCheckbox.isSelected = false
        }
      }
    }
    else if (SettingsSyncSettings.getInstance().syncEnabled) {
      val syncDisableOption = showDisableSyncDialog()
      if (syncDisableOption != DisableSyncType.DONT_DISABLE) {
        disableSyncOption.set(syncDisableOption)
        cellUserComboBox.comment?.text = message("sync.status.will.disable")
        handleDisableSync()
      } else {
        enableCheckbox.isSelected = true
      }
    } else {
      syncStatusChanged()
    }
  }

  private fun showDisableSyncDialog(): DisableSyncType {
    val providerName = selectedUser()?.providerName ?: ""
    val showRemoveDataCheckBox = SettingsSyncStatusTracker.getInstance().currentStatus != SettingsSyncStatusTracker.SyncStatus.UserActionRequired
    val disableSyncDialog = DisableSyncDialog(configPanel, providerName, showRemoveDataCheckBox)
    return if (disableSyncDialog.showAndGet()) {
      if (disableSyncDialog.removeDataSelected) {
        DisableSyncType.DISABLE_AND_REMOVE_DATA
      } else {
        DisableSyncType.DISABLE
      }
    }
    else {
      DisableSyncType.DONT_DISABLE
    }
  }

  private fun disableCurrentSyncDialog() : Boolean {
    val disableSyncConfirmed = yesNo(message("disable.active.sync.title"), message("disable.active.sync.message"))
      .yesText(message("disable.dialog.disable.button"))
      .noText(CommonBundle.getCancelButtonText())
      .ask(configPanel)
    if (disableSyncConfirmed) {
      enableCheckbox.isSelected = false
      disableSyncOption.set(DisableSyncType.DISABLE)
      handleDisableSync()
    }
    return disableSyncConfirmed
  }

  private fun repeatedDisableAndRemoveData(userAccount: UserProviderHolder) {
    if (currentUser() != userAccount) return
    val currentState = SettingsSyncLocalSettings.getInstance().remoteDataRemovalState
    if (currentState != RemoteDataRemovalState.ERROR.toString()) return
    disableAndRemoveData(userAccount, false)
  }

  private fun disableAndRemoveData(userAccount: UserProviderHolder, shouldStopSyncing: Boolean) {
    try {
      coroutineScope.launch {
        updateRemoveDataState(RemoteDataRemovalState.IN_PROGRESS)
        val project: Project? = ProjectManager.getInstanceIfCreated()?.openProjects?.firstOrNull()
        val result = try {
          withBackgroundProgress(
            currentOrDefaultProject(project),
            message("disable.remove.data.background.progress",
                    userAccount.toString()),
            false,
          ) {
            if (shouldStopSyncing) {
              sendRemoveRemoteDataEvent()
            } else {
              SettingsSyncBridge.removeRemoteData(userAccount.userData)
            }
          }
        } catch (ex : Exception) {
          DeleteServerDataResult.Error(ex.toString())
        }
        when (result) {
          is DeleteServerDataResult.Error -> {
            updateRemoveDataState(RemoteDataRemovalState.ERROR)
            syncStatusChanged()
            if (!isConfigPanelVisible()) {
              val notification = SettingsSyncRemoveDataNotifications.getFailedToDeleteSyncDataNotification(
                userAccount.toString(),
                { repeatedDisableAndRemoveData(userAccount) },
                currentUser()?.providerCode?.let { RemoteCommunicatorHolder.getProvider(it)?.authService?.contactSupportFunction }
              )
              Notifications.Bus.notify(notification, project)
            }
          }
          DeleteServerDataResult.Success -> {
            updateRemoveDataState(RemoteDataRemovalState.OK)
            syncStatusChanged()
            if (!isConfigPanelVisible()) {
              val notification = SettingsSyncRemoveDataNotifications.getSuccessfullyDeletedSyncDataNotification(userAccount.toString())
              Notifications.Bus.notify(notification, project)
            }
          }
        }
      }
    } catch (ex: Exception) {
      updateRemoveDataState(RemoteDataRemovalState.ERROR)
      syncStatusChanged()
      LOG.warn("Unexpected error during server data removal: ${ex.message}", ex)
    }
  }

  private fun isConfigPanelVisible(): Boolean {
    return ::configPanel.isInitialized && configPanel.isVisible && configPanel.isShowing
  }

  private suspend fun sendRemoveRemoteDataEvent(): DeleteServerDataResult {
    val result = suspendCancellableCoroutine { continuation ->
      SettingsSyncEvents.getInstance().fireSettingsChanged(
        SyncSettingsEvent.DeleteServerData { deleteResult ->
          continuation.resume(deleteResult) { _, _, _ -> }
        }
      )
    }
    return result
  }

  private fun updateSyncTypeBannerText() {
    val message = if (enableSyncOption.get() == InitSyncType.GET_FROM_SERVER) {
      message("enable.dialog.get.settings.from.account.text")
    } else if (enableSyncOption.get() == InitSyncType.PUSH_LOCAL) {
      message("enable.dialog.sync.local.settings.text")
    } else {
      ""
    }
    syncTypeBanner.setMessage(message)
  }

  private fun tryChangeAccount(selectedValue: UserProviderHolder) {
    updateRemoveDataState(RemoteDataRemovalState.OK)
    when {
      selectedValue == UserProviderHolder.ADD_ACCOUNT -> {
        if (SettingsSyncSettings.getInstance().syncEnabled && !disableCurrentSyncDialog()) {
          userComboBoxModel.selectedItem = userProviderHolder
          return
        }
        val syncTypeDialog = AddAccountDialog(configPanel, userAccountsList)
        if (syncTypeDialog.showAndGet()) {
          val providerCode = syncTypeDialog.providerCode
          val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: return
          login(provider, syncConfigPanel)
        }
        else {
          userComboBoxModel.selectedItem = userProviderHolder
          return
        }
      }
      selectedValue.userId == UserProviderHolder.LOGOUT_USER_ID -> {
        val logoutFunction = userProviderHolder?.let { user ->
          val provider = RemoteCommunicatorHolder.getProvider(user.providerCode)
          provider?.authService?.logoutFunction
        }
        if (logoutFunction != null) {
          coroutineScope.launch(ModalityState.current().asContextElement()) {
            withContext(Dispatchers.EDT) {
              logoutFunction(configPanel)
              if (updateUserAccountsList()) {
                configPanel.reset()
              } else {
                userComboBoxModel.selectedItem = userProviderHolder
                updateUserComboBoxModel()
              }
            }
          }
        } else {
          userComboBoxModel.selectedItem = userProviderHolder
        }
      }
      else -> {
        val wasEnabled = SettingsSyncSettings.getInstance().syncEnabled
        if (enableCheckbox.isSelected) {
          if (wasEnabled) {
            if (!disableCurrentSyncDialog()) {
              userComboBoxModel.selectedItem = userProviderHolder // setting old value
              return
            }
            enableCheckbox.doClick()
          }
          else {
            enableButtonAction()
          }
        }
        userProviderHolder = selectedValue
        if (updateUserAccountLogout(selectedValue)) {
          updateUserComboBoxModel()
        }
      }
    }
  }

  private fun updateUserAccountsList(): Boolean {
    val newList = arrayListOf<UserProviderHolder>()
    val providersList = RemoteCommunicatorHolder.getAvailableProviders()
    providersList.forEach { communicator ->
      val authService = communicator.authService
      val providerName = authService.providerName
      newList.addAll(authService.getAvailableUserAccounts().map { it.toUserProviderHolder(providerName) })
    }
    if (hasMultipleProviders.get()) {
      newList.add(UserProviderHolder.ADD_ACCOUNT)
    }
    if (newList != userAccountsList) {
      userAccountsList.clear()
      userAccountsList.addAll(newList)
      userAccountListIsNotEmpty.set(userAccountsList.any { it != UserProviderHolder.ADD_ACCOUNT })
      return true
    }
    return false
  }

  private fun updateUserAccountLogout(selectedUser: UserProviderHolder?): Boolean {
    val newUserAccountsLogout = selectedUser?.providerCode?.let {
      val provider = RemoteCommunicatorHolder.getProvider(it)
      provider?.authService?.logoutFunction?.let {
        UserProviderHolder.logout(provider.providerCode, provider.authService.providerName)
      }
    }
    if (newUserAccountsLogout != userAccountsLogout) {
      userAccountsLogout = newUserAccountsLogout
      return true
    }
    return false
  }

  private fun updateUserComboBoxModel() {
    val selectedUser = selectedUser()
    userComboBoxModel.update(userAccountsList + listOfNotNull(userAccountsLogout))
    userComboBoxModel.selectedItem = selectedUser
  }

  private fun validateCurrentUser() {
    val currentUser = currentUser()
    if (currentUser in userAccountsList) {
      userProviderHolder = currentUser
      return
    }
    // logout from an active account could happen somewhere else, should switch users manually
    val newCurrentUser = userAccountsList.firstOrNull { it != UserProviderHolder.ADD_ACCOUNT }
    if (SettingsSyncSettings.getInstance().syncEnabled) {
      handleDisableSync()
    }
    with(SettingsSyncLocalSettings.getInstance()) {
      userId = newCurrentUser?.userId
      providerCode = newCurrentUser?.providerCode
      remoteDataRemovalState = RemoteDataRemovalState.OK.toString()
    }
    userProviderHolder = newCurrentUser
  }


  private fun login(
    provider: SettingsSyncCommunicatorProvider,
    syncConfigPanel: DialogPanel,
  ) {
    coroutineScope.launch(ModalityState.current().asContextElement()) {
      val loginDisposable = Disposer.newDisposable("BackupAndSyncLoginDisposable")
      try {
        val userData = provider.authService.login(syncConfigPanel)
        if (userData != null) {
          withContext(Dispatchers.EDT) {
            updateUserAccountsList()
            val serverStateChecked = withContext(Dispatchers.IO) {
              val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userData.id, loginDisposable) ?: return@withContext false
              checkServerState(syncPanelHolder, remoteCommunicator, provider.authService.crossSyncSupported())
            }
            if (serverStateChecked) {
              SettingsSyncEvents.getInstance().fireLoginStateChanged()
              val newHolder = UserProviderHolder(userData.id, userData, provider.authService.providerCode, provider.authService.providerName, null)
              userProviderHolder = newHolder
              userComboBoxModel.selectedItem = newHolder
              updateUserAccountLogout(newHolder)
              updateUserComboBoxModel()

              enableCheckbox.isSelected = true
              wasUsedBefore.set(true)
              syncConfigPanel.reset()
              triggerUpdateConfigurable()
            } else {
              userComboBoxModel.selectedItem = userProviderHolder
              updateUserComboBoxModel()
            }
          }
        }
        else {
          LOG.info("Received empty user data from login")
          userComboBoxModel.selectedItem = userProviderHolder
        }
      }
      catch (ex: CancellationException) {
        LOG.info("Login procedure was cancelled")
        throw ex
      }
      catch (ex: Throwable) {
        LOG.warn("Error during login", ex)
        userComboBoxModel.selectedItem = userProviderHolder
      }
      finally {
        Disposer.dispose(loginDisposable)
      }
      syncConfigPanel.requestFocusInWindow()
    }
  }

  private fun SettingsSyncUserData.toUserProviderHolder(providerName: String, separatorString: String? = null) =
    UserProviderHolder(id, this, providerCode, providerName, separatorString)

  override fun syncStatusChanged() {
    if (!::cellUserComboBox.isInitialized)
      return
    if (updateUserAccountsList()) {
      if (updateUserAccountLogout(selectedUser())) {
        updateUserComboBoxModel()
      }
    }
    refreshActionRequired()
    if (!enableCheckbox.isSelected) {
      return
    }
    if (SettingsSyncSettings.getInstance().syncEnabled) {
      val currentStatus = SettingsSyncStatusTracker.getInstance().currentStatus
      if (currentStatus == SettingsSyncStatusTracker.SyncStatus.Success) {
        val lastSyncTime = SettingsSyncStatusTracker.getInstance().getLastSyncTime()
        if (lastSyncTime > 0) {
          cellUserComboBox.comment?.text = message("sync.status.last.sync.message", DateFormatUtil.formatPrettyDateTime(lastSyncTime))
        }
        else {
          cellUserComboBox.comment?.text = message("sync.status.enabled")
        }
      }
      else if (currentStatus is SettingsSyncStatusTracker.SyncStatus.Error) {
        cellUserComboBox.comment?.text = message("sync.status.failed", currentStatus.errorMessage)
      }
    }
    else {
      cellUserComboBox.comment?.text = ""
    }
  }

  private fun refreshActionRequired() {
    val userActionRequired: PendingUserAction? = getPendingUserAction()
    actionRequired.set(userActionRequired != null)
    if (userActionRequired != null) {
      actionRequiredAction = {
        userActionRequired.action(syncConfigPanel)
        refreshActionRequired()
        if (!SettingsSyncSettings.getInstance().syncEnabled) {
          enableButtonAction()
        }
      }
      actionRequiredLabel.text = userActionRequired.message
      actionRequiredButton.text = userActionRequired.actionTitle
      cellUserComboBox.comment?.text = message("sync.status.action.required.comment",
                                               userActionRequired.actionTitle,
                                               userActionRequired.actionDescription ?: userActionRequired.message)
    }
    else {
      cellUserComboBox.comment?.text = ""
      actionRequiredAction = null
      actionRequiredLabel.text = ""
      actionRequiredButton.text = ""
    }
  }

  private fun getPendingUserAction(): PendingUserAction? {
    if (!enableCheckbox.isSelected)
      return null
    return selectedUser()?.let {
      RemoteCommunicatorHolder.getProvider(it.providerCode)?.authService?.getPendingUserAction(it.userId)
    }
  }

  // triggers a fake action, which causes SettingEditor to update and check if configurable was modified
  // must be called on EDT
  private fun triggerUpdateConfigurable() {
    val dumbAwareAction = DumbAwareAction.create(Consumer { _: AnActionEvent? ->
      // do nothing
    })
    val event = AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, Presentation(), "", ActionUiKind.NONE, null)
    ActionUtil.performAction(dumbAwareAction, event)
  }



  private suspend fun checkServerState(
    syncPanelHolder: SettingsSyncPanelHolder,
    communicator: SettingsSyncRemoteCommunicator,
    crossSyncAvailable: Boolean,
  ) : Boolean {
    communicator.setTemporary(true)
    val updateResult = try {
      communicator.receiveUpdates()
    }
    catch (ex: Exception) {
      LOG.warn(ex.message)
      State.CANCELLED
    }
    when (updateResult) {
      NoFileOnServer, FileDeletedFromServer -> {
        syncPanelHolder.setSyncSettings(null)
        syncPanelHolder.setSyncScopeSettings(null)
        syncPanelHolder.crossSyncSupported.set(crossSyncAvailable)
        enableSyncOption.set(InitSyncType.PUSH_LOCAL)
        remoteSettingsExist.set(false)
        return true
      }
      is Success -> {
        syncPanelHolder.setSyncSettings(updateResult.settingsSnapshot.getState())
        syncPanelHolder.setSyncScopeSettings(SettingsSyncLocalStateHolder(updateResult.isCrossIdeSyncEnabled))
        syncPanelHolder.crossSyncSupported.set(crossSyncAvailable)
        enableSyncOption.set(InitSyncType.GET_FROM_SERVER)
        remoteSettingsExist.set(true)
        return true
      }
      is Error -> {
        if (updateResult != State.CANCELLED) {
          showErrorOnEDT(updateResult.message)
          return false
        }
      }
    }
    return false
  }

  private suspend fun showErrorOnEDT(message: String, title: String = message("notification.title.update.error")) {
    withContext(Dispatchers.EDT) {
      @Suppress("HardCodedStringLiteral")
      Messages.showErrorDialog(configPanel, message, title)
    }
  }

  override fun loginStateChanged() {
    if (wasUsedBefore.get() && currentUser() == null) {
      this.reset()
    }
  }

  private data class UserProviderHolder(
    val userId: String,
    val userData: SettingsSyncUserData,
    val providerCode: String,
    val providerName: String,
    val separatorString: String?, // separator value, set only for the first account in the list
  ) {
    companion object {
      val ADD_ACCOUNT = UserProviderHolder(
        "<ADD_ACCOUNT>", SettingsSyncUserData("<ADD_ACCOUNT>", "", null, null, message("enable.sync.add.account")), "",
        "", "")
      const val LOGOUT_USER_ID = "<LOGOUT>"
      fun logout(providerCode: String, providerName: String) = UserProviderHolder(
        LOGOUT_USER_ID, SettingsSyncUserData(LOGOUT_USER_ID, providerCode, null, null, LOGOUT_USER_ID), providerCode,
        providerName, "")
    }

    override fun toString(): String {
      return userData.printableName ?: userData.email ?: userData.name ?: userData.id
    }
  }

  private enum class InitSyncType {
    PUSH_LOCAL,
    GET_FROM_SERVER
  }

  private enum class DisableSyncType {
    DISABLE,
    DISABLE_AND_REMOVE_DATA,
    DONT_DISABLE
  }

  private enum class RemoteDataRemovalState {
    OK,
    IN_PROGRESS, // background progress of data removal is running
    ERROR; // data removal finished with an error, should show error panel

    companion object {
      fun fromString(value: String?): RemoteDataRemovalState {
        return entries.find { it.name == value } ?: OK
      }
    }
  }

  private fun updateRemoveDataState(newState: RemoteDataRemovalState) {
    SettingsSyncLocalSettings.getInstance().remoteDataRemovalState = newState.toString()
    when (newState) {
      RemoteDataRemovalState.OK -> {
        showRemoveDataErrorPanel.set(false)
        userAccountChangeEnabled.set(true)
      }
      RemoteDataRemovalState.IN_PROGRESS -> {
        showRemoveDataErrorPanel.set(false)
        userAccountChangeEnabled.set(false)
      }
      RemoteDataRemovalState.ERROR -> {
        showRemoveDataErrorPanel.set(true)
        userAccountChangeEnabled.set(true)
      }
    }
  }

  private class ChangeSyncTypeDialog(parent: JComponent, var option: InitSyncType) : DialogWrapper(parent, false) {

    init {
      title = message("title.settings.sync")
      init()
    }

    override fun createContentPaneBorder(): Border {
      val insets = JButton().insets
      return JBUI.Borders.empty(20, 20, 20 - insets.bottom, 20 - insets.right)
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              @Suppress("DialogTitleCapitalization")
              text(message("enable.dialog.source.option.title")).applyToComponent {
                font = JBFont.h4()
              }
            }
            row {
              text(message("enable.dialog.source.option.text"), 50)
            }
            buttonsGroup ("", false) {
              row {
                radioButton(message("enable.dialog.get.settings.from.account.option"), InitSyncType.GET_FROM_SERVER)
              }
              row {
                radioButton(message("enable.dialog.sync.local.settings.option"), InitSyncType.PUSH_LOCAL)
              }
            }.bind(::option)
          }
        }
      }
    }

    override fun createActions(): Array<Action> =
      arrayOf(okAction, cancelAction)
  }

  private class DisableSyncDialog(parent: JComponent, val providerName: String, val showRemoveDataCheckBox: Boolean) : DialogWrapper(parent, false) {
    init {
      title = message("title.settings.sync")
      init()
    }

    var removeDataSelected = false

    override fun createContentPaneBorder(): Border {
      val insets = JButton().insets
      return JBUI.Borders.empty(20, 20, 20 - insets.bottom, 20 - insets.right)
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              @Suppress("DialogTitleCapitalization")
              text(message("disable.dialog.title")).applyToComponent {
                font = JBFont.h4()
              }
            }
            row {
              text(message("disable.dialog.text", ApplicationNamesInfo.getInstance().fullProductName))
            }
            row {
              if (showRemoveDataCheckBox) {
                checkBox(message("disable.dialog.remove.data.box", providerName))
                  .bindSelected(::removeDataSelected)
              }
            }
          }
        }
      }
    }


    override fun createActions(): Array<Action> =
      arrayOf(okAction, cancelAction)
  }

  private class AddAccountDialog(parent: JComponent, private val currentUserAccounts: List<UserProviderHolder>) : DialogWrapper(parent, false) {

    var providerCode: String = ""
    private val loginAction = object : DialogWrapperAction(message("enable.sync.choose.data.provider.login.button")) {
      init {
        putValue(DEFAULT_ACTION, true)
        isEnabled = false
      }

      override fun doAction(e: ActionEvent?) {
        close(OK_EXIT_CODE)
      }
    }

    init {
      title = message("title.settings.sync")
      init()
    }

    override fun createContentPaneBorder(): Border {
      val insets = JButton().insets
      return JBUI.Borders.empty(20, 20, 20 - insets.bottom, 20 - insets.right)
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              @Suppress("DialogTitleCapitalization")
              text(message("enable.sync.choose.data.provider.title")).applyToComponent {
                font = JBFont.h4()
              }
            }
            val availableProviders = RemoteCommunicatorHolder.getAvailableProviders().filter { it.isAvailable() }
            availableProviders.firstOrNull { it.learnMoreLinkPair2 != null }?.also {
              row {
                val linkPair = it.learnMoreLinkPair2!!
                @Suppress("HardCodedStringLiteral")
                browserLink(linkPair.first, linkPair.second)
              }
            }
            val currentUserProviders = currentUserAccounts.filter { it != UserProviderHolder.ADD_ACCOUNT }.map { it.providerCode }.toSet()

            row {
              text(message("enable.sync.choose.data.provider.text"))
            }

            val buttonGroup = ButtonGroup()
            val radioButtonPanel = JPanel().apply {
              layout = BoxLayout(this, BoxLayout.X_AXIS)
              isOpaque = false
            }

            for ((index, provider) in availableProviders.withIndex()) {
              if (index > 0) {
                radioButtonPanel.add(Box.createHorizontalStrut(5))
              }
              val isEnabled = provider.supportsMultipleAccounts || !currentUserProviders.contains(provider.providerCode)
              val providerPanel = createRadioButtonPanelForProvider(provider, buttonGroup, isEnabled)
              radioButtonPanel.add(providerPanel)
            }
            row {
              cell(radioButtonPanel)
            }
          }
        }
      }
    }

    override fun createActions(): Array<Action> =
      arrayOf(loginAction, cancelAction)


    private fun createRadioButtonPanelForProvider(provider: SettingsSyncCommunicatorProvider, buttonGroup: ButtonGroup, enabled: Boolean): JPanel {
      val alreadyLoggedInMessage = message("enable.sync.provider.already.logged.in", provider.authService.providerName)
      val radioButton = JBRadioButton().apply {
        actionCommand = provider.providerCode
        this.isEnabled = enabled
        if (!enabled) toolTipText = alreadyLoggedInMessage
        addActionListener {
          if (isSelected && enabled) {
            providerCode = provider.providerCode
            loginAction.isEnabled = true
          }
        }
      }
      buttonGroup.add(radioButton)

      // mouse listener for text and icon to mimic normal radiobutton behavior
      val mouseListener = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          radioButton.doClick()
        }
      }

      val iconLabel = provider.authService.icon?.let {
        JLabel(it).apply {
          if (enabled) addMouseListener(mouseListener)
          if (!enabled) toolTipText = alreadyLoggedInMessage
          if (!enabled) icon = IconLoader.getDisabledIcon(provider.authService.icon!!)
        }
      }
      val textLabel = JLabel(provider.authService.providerName).apply {
        if (enabled) addMouseListener(mouseListener)
        if (!enabled) toolTipText = alreadyLoggedInMessage
        isEnabled = enabled
      }
      return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false

        add(radioButton)
        if (iconLabel != null) {
          add(iconLabel)
          add(Box.createHorizontalStrut(4))
        }
        add(textLabel)
      }
    }
  }
}

class SettingsSyncConfigurableProvider(private val coroutineScope: CoroutineScope) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable(coroutineScope)
}