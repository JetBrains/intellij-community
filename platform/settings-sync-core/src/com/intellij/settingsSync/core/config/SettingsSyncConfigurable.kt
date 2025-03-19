package com.intellij.settingsSync.core.config


import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.UpdateResult.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.core.config.SettingsSyncEnabler.State
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.Consumer
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.labelFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.event.HyperlinkEvent

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
  private lateinit var syncConfigPanel: DialogPanel


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
    syncConfigPanel = syncPanelHolder.createCombinedSyncSettingsPanel(message("configurable.what.to.sync.label"),
                                                                      SettingsSyncSettings.getInstance(),
                                                                      SettingsSyncLocalSettings.getInstance())

    configPanel = panel {
      enabledStatus.set(SettingsSyncSettings.getInstance().syncEnabled)
      var userProviderHolder: UserProviderHolder? = null
      if (SettingsSyncLocalSettings.getInstance().userId != null && SettingsSyncLocalSettings.getInstance().providerCode != null) {
        val authService = RemoteCommunicatorHolder.getProvider(SettingsSyncLocalSettings.getInstance().providerCode!!)?.authService
        syncPanelHolder.crossSyncSupported.set(authService?.crossSyncSupported() ?: true)
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
        userDropDownLink = DropDownLink<UserProviderHolder?>(userProviderHolder) { link: DropDownLink<UserProviderHolder?>? -> showAccounts(link) }
        cell(userDropDownLink).onChangedContext { component, context ->
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
        }
      }.visibleIf(wasUsedBefore)

      row {
        val enableButtonCell = button(message("config.button.enable"), ::enableButtonAction)
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
              enabledStatus.get() != SettingsSyncSettings.getInstance().syncEnabled
              || syncConfigPanel.isModified()
              || userDropDownLink.selectedItem?.userId != SettingsSyncLocalSettings.getInstance().userId
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

  private fun enableButtonAction(event: ActionEvent){
    if (SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired) {
      val actionRequired = SettingsSyncStatusTracker.getInstance().currentStatus as SettingsSyncStatusTracker.SyncStatus.ActionRequired
      runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), actionRequired.actionTitle) {
        actionRequired.execute()
      }
      return
    }
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
        if (checkServerState(syncPanelHolder, remoteCommunicator, provider.authService.crossSyncSupported())) {
          enabledStatus.set(true)
          triggerUpdateConfigurable()
        }
      }
    }
    else {
      val syncDisableOption = showDisableSyncDialog()
      if (syncDisableOption != DisableSyncType.DONT_DISABLE) {
        enabledStatus.set(false)
        disableSyncOption.set(syncDisableOption)
      }
    }
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

  private fun disableCurrentSyncDialog() {
    val code = MessagesService.getInstance().showMessageDialog(
      null, null, message("disable.active.sync.message"), message("disable.active.sync.title"),
      arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
      1, -1, Messages.getInformationIcon(), null, false, null
    )
    if (code == 1) {
      enabledStatus.set(false)
      disableSyncOption.set(DisableSyncType.DISABLE)
    }
  }

  private fun disableAndRemoveData() {
    runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("disable.remove.data.title"), TaskCancellation.cancellable()) {
      val cdl = CountDownLatch(1)
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData { result ->
        cdl.countDown()

        when (result) {
          is DeleteServerDataResult.Error -> {
            statusLabel.icon = AllIcons.General.Error
            @Suppress("HardCodedStringLiteral")
            statusLabel.text = message("sync.status.failed", "${message("disable.remove.data.failure")}: ${result.error}")
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

  private fun showAccounts(link: DropDownLink<UserProviderHolder?>?): JBPopup {
    val accounts = object : BaseListPopupStep<UserProviderHolder>() {
      private var stepSelectedValue: UserProviderHolder? = null
      override fun onChosen(selectedValue: UserProviderHolder, finalChoice: Boolean): PopupStep<*>? {
        stepSelectedValue = selectedValue
        return PopupStep.FINAL_CHOICE
      }

      override fun getTextFor(value: UserProviderHolder?): String {
        return if (value == UserProviderHolder.addAccount) {
          message("enable.sync.add.account")
        }
        else {
          value?.toString() ?: ""
        }
      }

      override fun getSeparatorAbove(value: UserProviderHolder?): ListSeparator? {
        return value?.separatorString?.let { ListSeparator(it) }
      }

      override fun getFinalRunnable(): Runnable? {
        if (stepSelectedValue != null) {
          return Runnable { tryChangeAccount(stepSelectedValue!!) }
        }
        return null
      }

      init {
        init(null, userAccountsList, emptyList())
        defaultOptionIndex = userAccountsList.indexOf(userDropDownLink.selectedItem)
      }
    }

    val currentProviderCode = link?.selectedItem?.providerCode

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(configPanel))
    val provider = currentProviderCode?.let { RemoteCommunicatorHolder.getProvider(it ) }
    val logoutFunction = provider?.authService?.logoutFunction
    val listPopup = object : ListPopupImpl(project, accounts) {

      override fun setFooterComponent(c: JComponent?) {
        val thePopup = this
        if (logoutFunction == null) {
          return super.setFooterComponent(c)
        }
        super.setFooterComponent(DslLabel(DslLabelType.LABEL).apply {
          text = "<a>${message("logout.link.text", currentProviderCode ?: "")}</a>"

          addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
              thePopup.cancel()
              coroutineScope.launch(ModalityState.current().asContextElement()) {
                withContext(Dispatchers.EDT) {
                  logoutFunction(configPanel)
                }
              }
            }
          }

          foreground = JBUI.CurrentTheme.Advertiser.foreground()
          background = JBUI.CurrentTheme.Advertiser.background()

          setOpaque(true)
          setFont(RelativeFont.NORMAL.scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get(), scale(11f)).derive(labelFont))
          setBorder(JBUI.CurrentTheme.Advertiser.border())
        })
      }
    }
    if (currentProviderCode != null && logoutFunction != null) {
      listPopup.setAdText(message("logout.link.text", currentProviderCode)) // doesn't matter, will be changed
    }

    return listPopup
  }

  private fun tryChangeAccount(selectedValue: UserProviderHolder) {
    if (enabledStatus.get() || SettingsSyncSettings.getInstance().syncEnabled) {
      disableCurrentSyncDialog()
    } else if (selectedValue == UserProviderHolder.addAccount) {
      val syncTypeDialog = AddAccountDialog(configPanel)
      if (syncTypeDialog.showAndGet()) {
        val providerCode = syncTypeDialog.providerCode
        val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: return
        login(provider, syncConfigPanel)
      }
    } else {
      userDropDownLink.selectedItem = selectedValue
    }

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
            if (checkServerState(syncPanelHolder, remoteCommunicator, provider.authService.crossSyncSupported())) {
              SettingsSyncEvents.getInstance().fireLoginStateChanged()
              userDropDownLink.selectedItem = UserProviderHolder(userData.id, userData, provider.authService.providerCode,
                                                                 provider.authService.providerName, null)
              enabledStatus.set(true)
              wasUsedBefore.set(true)
              syncConfigPanel.reset()
              triggerUpdateConfigurable()
            }
          }
        }
        else {
          LOG.info("Received empty user data from login")
        }
      }
      catch (ex: CancellationException) {
        LOG.info("Login procedure was cancelled")
        throw ex
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
    if (!::statusLabel.isInitialized)
      return
    if (!enabledStatus.get()) {
      statusLabel.icon = icons.SettingsSyncIcons.StatusDisabled
      statusLabel.text = message("sync.status.disabled.message")
      enableButton.text = message("config.button.enable")
      return
    }
    if (SettingsSyncSettings.getInstance().syncEnabled) {
      enableButton.text = message("config.button.disable")
      val currentStatus = SettingsSyncStatusTracker.getInstance().currentStatus
      when(currentStatus) {
        SettingsSyncStatusTracker.SyncStatus.Success -> {
          statusLabel.icon = icons.SettingsSyncIcons.StatusEnabled
          val lastSyncTime = SettingsSyncStatusTracker.getInstance().getLastSyncTime()
          if (lastSyncTime > 0) {
            statusLabel.text = message("sync.status.last.sync.message", DateFormatUtil.formatPrettyDateTime(lastSyncTime))
          } else {
            statusLabel.text = message("sync.status.enabled")
          }
        }
        is SettingsSyncStatusTracker.SyncStatus.Error -> {
          statusLabel.text = message("sync.status.failed", currentStatus.errorMessage)
          statusLabel.icon = AllIcons.General.Error
        }
        is SettingsSyncStatusTracker.SyncStatus.ActionRequired -> {
          statusLabel.text = message("sync.status.action.required", currentStatus.message)
          statusLabel.icon = AllIcons.General.BalloonWarning
          enableButton.text = currentStatus.actionTitle
        }
      }
    }
    else {
      statusLabel.icon = icons.SettingsSyncIcons.StatusNotRun
      statusLabel.text = message("sync.status.enabled")
    }
  }

  // triggers fake action, which causes SettingEditor to update and check if configurable was modified
  private fun triggerUpdateConfigurable() {
    val dumbAwareAction = DumbAwareAction.create(Consumer { _: AnActionEvent? ->
      // do nothing
    })
    val event = AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, Presentation(), "", ActionUiKind.NONE, null)
    ActionUtil.performActionDumbAwareWithCallbacks(dumbAwareAction, event)
  }



  private fun checkServerState(syncPanelHolder: SettingsSyncPanelHolder,
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