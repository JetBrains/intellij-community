package com.intellij.settingsSync.core.config


import com.intellij.icons.AllIcons
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.*
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.UpdateResult.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.core.config.SettingsSyncEnabler.State
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.groupedTextListCellRenderer
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.ItemEvent
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.*

internal class SettingsSyncConfigurable(private val coroutineScope: CoroutineScope) : BoundConfigurable(message("title.settings.sync")),
                                                                                      SettingsSyncEnabler.Listener,
                                                                                      SettingsSyncStatusTracker.Listener {
  companion object {
    private val LOG = logger<SettingsSyncConfigurable>()
  }

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: JButton
  private lateinit var statusLabel: JLabel
  private lateinit var userDropDownLink: DropDownLink<UserProviderHolder?>
  private lateinit var syncTypeLabel: JEditorPane

  private val syncEnabler = SettingsSyncEnabler()
  private val enabledStatus = AtomicBooleanProperty(false)
  private val enableSyncOption = AtomicProperty<InitSyncType>(InitSyncType.GET_FROM_SERVER)
  private val disableSyncOption = AtomicProperty<Int>(DisableSyncType.DISABLE)
  private val remoteSettingsExist = AtomicBooleanProperty(false)
  private val wasUsedBefore = AtomicBooleanProperty(SettingsSyncLocalSettings.getInstance().userId != null)
  private val userAccountsList = arrayListOf<UserProviderHolder>()
  private val syncPanelHolder = SettingsSyncPanelHolder()
  private val hasMultipleProviders = AtomicBooleanProperty(RemoteCommunicatorHolder.getExternalProviders().isNotEmpty())

  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
  }

  override fun createPanel(): DialogPanel {
    val syncConfigPanel = syncPanelHolder.createCombinedSyncSettingsPanel(message("configurable.what.to.sync.label"),
                                                                          SettingsSyncSettings.getInstance(),
                                                                          SettingsSyncLocalSettings.getInstance())

    configPanel = panel {
      enabledStatus.set(SettingsSyncSettings.getInstance().syncEnabled)
      var userProviderHolder: UserProviderHolder? = null
      if (SettingsSyncLocalSettings.getInstance().userId != null && SettingsSyncLocalSettings.getInstance().providerCode != null) {
        val authService = RemoteCommunicatorHolder.getProvider(SettingsSyncLocalSettings.getInstance().providerCode!!)?.authService
        if (authService != null) {
          authService.getAvailableUserAccounts().find {
            it.id == SettingsSyncLocalSettings.getInstance().userId
          }?.apply {
            userProviderHolder = toUserProviderHolder(authService.providerName)
          }
        }
      }

      updateUserAccountsList()
      enabledStatus.afterChange {
        syncStatusChanged()
      }

      row {
        label(message("settings.sync.info.message"))
      }.visibleIf(enabledStatus.not())

      row {
        label(message("settings.sync.select.provider.message"))
      }.visibleIf(enabledStatus.not())

      row {
        val availableProviders = RemoteCommunicatorHolder.getAvailableProviders()
        availableProviders.forEach { provider ->
          button(provider.authService.providerName) {
            login(provider, syncConfigPanel)
          }.applyToComponent {
            icon = provider.authService.icon
          }
        }
      }.visibleIf(wasUsedBefore.not().and(hasMultipleProviders))

      row {
        val defaultProvider = RemoteCommunicatorHolder.getDefaultProvider() ?: return@row
        button(message("config.button.login")) {
          login(defaultProvider, syncConfigPanel)
        }
      }.visibleIf(wasUsedBefore.not().and(hasMultipleProviders.not()))

      row {
        val label = label("").applyToComponent {
          iconTextGap = 6
        }.gap(RightGap.SMALL)
        statusLabel = label.component
        cell(object: DropDownLink<UserProviderHolder?>(userProviderHolder, userAccountsList) {
          override fun createRenderer(): ListCellRenderer<in UserProviderHolder?> {
            return groupedTextListCellRenderer({
                                                 if (it == UserProviderHolder.addAccount) {
                                                   message("enable.sync.add.account")
                                                 } else {
                                                   it.toString()
                                                 }

                                               }, {
                                                 it?.separatorString
            })
          }
        }).onChangedContext { component, context ->
          val event = context.event
          if (event is ItemEvent && event.item == UserProviderHolder.addAccount) {
            val syncTypeDialog = AddAccountDialog(configPanel)
            if (syncTypeDialog.showAndGet()) {
              val providerCode = syncTypeDialog.providerCode
              val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: return@onChangedContext
              component.selectedItem = null
              component.text = ""
              login(provider, syncConfigPanel)
            }
          } else {
            component.text = component.selectedItem.toString()
          }
        }.apply {
          userDropDownLink = this.component
        }

      }.visibleIf(wasUsedBefore)

      row {
        val enableButtonCell = button(message("config.button.enable")) {
          if (!enabledStatus.get()) {
            runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("enable.sync.check.server.data.progress")) {
              val (userId, userData, providerCode, providerName) = userDropDownLink.selectedItem ?: run {
                LOG.warn("No selected user")
                return@runWithModalProgressBlocking
              }
              val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: run {
                LOG.warn("Provider '$providerName' ($providerCode) is not available")
                return@runWithModalProgressBlocking
              }
              val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userId) ?: run {
                LOG.warn("Cannot create remote communicator of type '$providerName' ($providerCode)")
                return@runWithModalProgressBlocking
              }
              if (checkServerState(syncPanelHolder, remoteCommunicator)) {
                enabledStatus.set(true)
                syncStatusChanged()
              }
            }
          } else {
            val syncDisableOption = showDisableSyncDialog()
            if (syncDisableOption != DisableSyncType.DONT_DISABLE) {
              enabledStatus.set(false)
              disableSyncOption.set(syncDisableOption)
              syncStatusChanged()
            }
          }
        }
        enableButton = enableButtonCell.component
      }.visibleIf(wasUsedBefore)


      // settings to sync
      group(message("enable.dialog.select.what.to.sync")) {
        row {
          icon(AllIcons.General.BalloonWarning).applyToComponent {
            isOpaque = true
            background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND
            border = JBUI.Borders.compound(
              JBUI.Borders.customLine(JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR, 1, 1, 1, 0),
              JBUI.Borders.empty(8)
            )
            verticalAlignment = SwingConstants.TOP
          }.align(AlignY.FILL)
          text("",
               action = {
                 val syncTypeDialog = ChangeSyncTypeDialog(configPanel, enableSyncOption.get())
                 if (syncTypeDialog.showAndGet()) {
                   enableSyncOption.set(syncTypeDialog.option)
                 }
               }).applyToComponent {
            isOpaque = true
            background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND
            border = JBUI.Borders.compound(
              JBUI.Borders.customLine(JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR, 1, 0, 1, 1),
              JBUI.Borders.empty(8)
            )
          }.align(AlignX.FILL).resizableColumn().also {
            syncTypeLabel = it.component
            enableSyncOption.afterChange {
              updateSyncOptionText()
            }
          }
          cell()
        }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL)
          .visibleIf(remoteSettingsExist.and(enabledStatus))

        row {
          cell(syncConfigPanel)
            .onReset(syncConfigPanel::reset)
            .onIsModified{
              enabledStatus.get() != SettingsSyncSettings.getInstance().syncEnabled || syncConfigPanel.isModified()
            }
            .onApply {
              with(SettingsSyncLocalSettings.getInstance()) {
                userId = userDropDownLink.selectedItem?.userId
                providerCode = userDropDownLink.selectedItem?.providerCode
              }
              if (enabledStatus.get()) {
                syncConfigPanel.apply()
              }
              if (SettingsSyncSettings.getInstance().syncEnabled != enabledStatus.get()) {
                if (enabledStatus.get()) {
                  SettingsSyncSettings.getInstance().syncEnabled = enabledStatus.get()
                  if (enableSyncOption.get() == InitSyncType.GET_FROM_SERVER) {
                    syncEnabler.getSettingsFromServer()
                  }
                  else {
                    syncEnabler.pushSettingsToServer()
                  }
                } else {
                  when (disableSyncOption.get()) {
                    DisableSyncType.DISABLE_AND_REMOVE_DATA -> {
                      disableAndRemoveData()
                      SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(
                        SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_AND_REMOVED_DATA_FROM_SERVER)
                    }
                    DisableSyncType.DISABLE -> {
                      SettingsSyncSettings.getInstance().syncEnabled = false
                      syncStatusChanged()
                      SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
                    }
                    else -> {
                      SettingsSyncSettings.getInstance().syncEnabled = false
                      syncStatusChanged()
                      SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
                    }
                  }
                  SettingsSyncSettings.getInstance().syncEnabled = enabledStatus.get()
                }
                // clear the flag
                remoteSettingsExist.set(false)
              }
            }
        }.topGap(TopGap.SMALL)
      }.visibleIf(enabledStatus)

      // apply necessary changes
    }
    syncStatusChanged()
    return configPanel
  }

  private fun showDisableSyncDialog(): Int {
    @Suppress("DialogTitleCapitalization")
    val providerName = userDropDownLink.selectedItem?.providerName ?: ""
    return Messages.showCheckboxMessageDialog( // TODO<rv>: Use AlertMessage instead
      message("disable.dialog.text", providerName),
      message("disable.dialog.title"),
      arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
      message("disable.dialog.remove.data.box", providerName),
      false,
      1,
      1,
      Messages.getInformationIcon()
    ) { index: Int, checkbox: JCheckBox ->
      if (index == 1) {
        if (checkbox.isSelected) DisableSyncType.DISABLE_AND_REMOVE_DATA else DisableSyncType.DISABLE
      }
      else {
        0
      }
    }
  }

  private fun disableAndRemoveData() {
    runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("disable.remove.data.title"), TaskCancellation.cancellable()) {
      val cdl = CountDownLatch(1)
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData { result ->
        cdl.countDown()

        when (result) {
          is DeleteServerDataResult.Error -> {
            val messageBuilder = StringBuilder()
            messageBuilder.append(message("sync.status.failed"))
            statusLabel.icon = AllIcons.General.Error
            messageBuilder.append(' ').append("${message("disable.remove.data.failure")}: ${result.error}")
            @Suppress("HardCodedStringLiteral")
            statusLabel.text = messageBuilder.toString()
          }
          DeleteServerDataResult.Success -> {
            syncStatusChanged()
          }
        }
      })
      cdl.await(1, TimeUnit.MINUTES)
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun updateSyncOptionText() {
    val message = if (enableSyncOption.get() == InitSyncType.GET_FROM_SERVER) {
      message("enable.dialog.get.settings.from.account.text")
    } else if (enableSyncOption.get() == InitSyncType.PUSH_LOCAL) {
      message("enable.dialog.sync.local.settings.text")
    } else {
      ""
    }
    syncTypeLabel.text ="<div>$message</div> <div style='margin-top: 5px'><a>${message("enable.dialog.change")}</a></div>"
  }


  private fun updateUserAccountsList() {
    userAccountsList.clear()
    val providersMap = RemoteCommunicatorHolder.getAvailableProviders().map { it.providerCode to it }.toMap()
    providersMap.forEach { providerId, communicator ->
      val authService = communicator.authService
      val providerName = authService.providerName
      authService.getAvailableUserAccounts().forEachIndexed { idx, account ->
        val separatorString = if (idx == 0)
          providerName
        else
          null
        userAccountsList.add(account.toUserProviderHolder(providerName, separatorString))
      }
    }
    if (hasMultipleProviders.get()) {
      userAccountsList.add(UserProviderHolder.addAccount)
    }
  }

  private fun login(
    provider: SettingsSyncCommunicatorProvider,
    syncConfigPanel: DialogPanel,
  ) {
    coroutineScope.launch(ModalityState.current().asContextElement()) {
      try {
        val userData = provider.authService.login(syncConfigPanel)
        if (userData != null) {
          withContext(Dispatchers.EDT) {
            updateUserAccountsList()
            val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userData.id) ?: return@withContext
            if (checkServerState(syncPanelHolder, remoteCommunicator)) {
              SettingsSyncEvents.getInstance().fireLoginStateChanged()
              userDropDownLink.selectedItem = UserProviderHolder(userData.id, userData, provider.authService.providerCode,
                                                                 provider.authService.providerName, null)
              userDropDownLink.text
              enabledStatus.set(true)
              wasUsedBefore.set(true)
              syncStatusChanged()
              syncConfigPanel.reset()
            }
          }
        }
        else {
          LOG.info("Received empty user data from login")
        }
      }
      catch (ex: CancellationException) {
        LOG.info("Login procedure was cancelled")
        if (LOG.isDebugEnabled) {
          LOG.info("Login procedure was cancelled", ex)
        }
      }
      catch (ex: Throwable) {
        LOG.warn("Error during login", ex)
      }
      syncConfigPanel.requestFocusInWindow()
    }
  }

  private fun SettingsSyncUserData.toUserProviderHolder(providerName: String, separatorString: String? = null) =
    UserProviderHolder(id, this, providerCode, providerName, separatorString)

  override fun syncStatusChanged() {
    if (::statusLabel.isInitialized) {
      if (enabledStatus.get()) {
        val messageBuilder = StringBuilder()
        if (SettingsSyncSettings.getInstance().syncEnabled) {
          val statusTracker = SettingsSyncStatusTracker.getInstance()
          if (statusTracker.isSyncSuccessful()) {
            statusLabel.icon = icons.SettingsSyncIcons.StatusEnabled
            if (statusTracker.isSynced()) {
              messageBuilder.append(message("sync.status.last.sync.message", getReadableSyncTime()))
            } else {
              messageBuilder.append(message("sync.status.enabled"))
            }
          }
          else {
            messageBuilder.append(message("sync.status.failed"))
            statusLabel.icon = AllIcons.General.Error
            messageBuilder.append(' ').append(statusTracker.getErrorMessage())
          }
        }
        else {
          statusLabel.icon = icons.SettingsSyncIcons.StatusNotRun
          messageBuilder.append(message("sync.status.enabled"))
        }
        @Suppress("HardCodedStringLiteral") // The above strings are localized
        statusLabel.text = messageBuilder.toString()
        enableButton.text = message("config.button.disable")
      }
      else {
        statusLabel.icon = icons.SettingsSyncIcons.StatusDisabled
        statusLabel.text = message("sync.status.disabled.message")
        enableButton.text = message("config.button.enable")
      }
    }
  }

  private fun getReadableSyncTime(): String =
    DateFormatUtil.formatPrettyDateTime(SettingsSyncStatusTracker.getInstance().getLastSyncTime())


  private fun checkServerState(syncPanelHolder: SettingsSyncPanelHolder, communicator: SettingsSyncRemoteCommunicator) : Boolean {
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
        enableSyncOption.set(InitSyncType.PUSH_LOCAL)
        remoteSettingsExist.set(false)
        return true
      }
      is Success -> {
        syncPanelHolder.setSyncSettings(updateResult.settingsSnapshot.getState())
        syncPanelHolder.setSyncScopeSettings(SettingsSyncLocalStateHolder(updateResult.isCrossIdeSyncEnabled))
        enableSyncOption.set(InitSyncType.GET_FROM_SERVER)
        remoteSettingsExist.set(true)
        return true
      }
      is Error -> {
        if (updateResult != SettingsSyncEnabler.State.CANCELLED) {
          //showError(message("notification.title.update.error"), state.message)
          return false
        }
      }
    }
    return false
  }

  private data class UserProviderHolder(
    val userId: String,
    val userData: SettingsSyncUserData,
    val providerCode: String,
    val providerName: String,
    val separatorString: String?, // separator value, set only for the first account in the list
  ) {
    companion object{
      val addAccount = UserProviderHolder(
        "<ADDACCOUNT>", SettingsSyncUserData("<ADDACCOUNT>", "", null, null), "",
        "", "")
    }

    override fun toString(): String {
      return userData.printableName ?: userData.email ?: userData.name ?: userData.id
    }
  }

  private enum class InitSyncType {
    PUSH_LOCAL,
    GET_FROM_SERVER
  }

  private sealed class DisableSyncType{
    companion object{
      const val DISABLE = 1
      const val DISABLE_AND_REMOVE_DATA = 2
      const val DONT_DISABLE = 0
    }
  }


  private class ChangeSyncTypeDialog(parent: JComponent, var option: InitSyncType) : DialogWrapper(parent, false) {

    init {
      title = message("title.settings.sync")
      init()
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              text(message("enable.dialog.source.option.title")).bold()
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
      arrayOf(cancelAction, okAction)
  }

  private class AddAccountDialog(parent: JComponent) : DialogWrapper(parent, false) {

    var providerCode: String = ""

    init {
      title = message("title.settings.sync")
      init()
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              text(message("enable.sync.choose.data.provider.title")).bold()
            }
            buttonsGroup (message("enable.sync.choose.data.provider.text"), false) {
              val availableProviders = RemoteCommunicatorHolder.getAvailableProviders()
              row {
                for (provider in availableProviders) {
                  radioButton(provider.authService.providerName, provider.providerCode)
                }
              }
            }.bind(::providerCode)
          }
        }
      }
    }
  }
}

class SettingsSyncConfigurableProvider(private val coroutineScope: CoroutineScope) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SettingsSyncConfigurable(coroutineScope)
}