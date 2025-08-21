@file:OptIn(IntellijInternalApi::class)

package com.intellij.settingsSync.core.config


import com.intellij.BundleBase
import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
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
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.UpdateResult.*
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService.PendingUserAction
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.core.communicator.getAvailableSyncProviders
import com.intellij.settingsSync.core.config.SettingsSyncEnabler.State
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.Consumer
import com.intellij.util.asDisposable
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StartupUiUtil.labelFont
import kotlinx.coroutines.*
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CancellationException
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.HyperlinkEvent

internal class SettingsSyncConfigurable(private val coroutineScope: CoroutineScope) : BoundConfigurable(message("title.settings.sync")),
                                                                                      SettingsSyncEnabler.Listener,
                                                                                      SettingsSyncStatusTracker.Listener,
                                                                                      SettingsSyncEventListener {
  companion object {
    private val LOG = logger<SettingsSyncConfigurable>()
  }

  private lateinit var configPanel: DialogPanel
  private lateinit var enableCheckbox: JCheckBox
  private lateinit var cellDropDownLink: Cell<DropDownLink<UserProviderHolder?>>
  private lateinit var userDropDownLink: DropDownLink<UserProviderHolder?>
  private lateinit var syncTypeLabel: JBHtmlPane
  private lateinit var syncConfigPanel: DialogPanel


  private val syncEnabler = SettingsSyncEnabler()
  private val enableSyncOption = AtomicProperty<InitSyncType>(InitSyncType.GET_FROM_SERVER)
  private val disableSyncOption = AtomicProperty<DisableSyncType>(DisableSyncType.DISABLE)
  private val remoteSettingsExist = AtomicBooleanProperty(false)
  private val wasUsedBefore = AtomicBooleanProperty(currentUser() != null)
  private val userAccountsList = arrayListOf<UserProviderHolder>()
  private val userAccountListIsNotEmpty = AtomicBooleanProperty(false)
  private val syncPanelHolder = SettingsSyncPanelHolder()
  private val hasMultipleProviders = AtomicBooleanProperty(RemoteCommunicatorHolder.getExternalProviders().isNotEmpty())
  private var lastRemoveRemoteDataError: String? = null

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

  private fun currentUser(): UserProviderHolder? {
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: return null
    val providerCode = SettingsSyncLocalSettings.getInstance().providerCode ?: return null
    val authService = RemoteCommunicatorHolder.getProvider(providerCode)?.authService ?: return null
    return authService.getAvailableUserAccounts().find {
        it.id == userId
      }?.toUserProviderHolder(authService.providerName)
  }

  override fun createPanel(): DialogPanel {
    syncConfigPanel = syncPanelHolder.createCombinedSyncSettingsPanel(message("configurable.what.to.sync.label"),
                                                                      SettingsSyncSettings.getInstance(),
                                                                      SettingsSyncLocalSettings.getInstance())

    configPanel = panel {
      updateUserAccountsList()
      val userProviderHolder: UserProviderHolder? = currentUser() ?: userAccountsList.firstOrNull { it != UserProviderHolder.addAccount }
      val authService = userProviderHolder?.let { RemoteCommunicatorHolder.getProvider(userProviderHolder.providerCode) } ?.authService
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
        }

        row {
          val availableProviders = RemoteCommunicatorHolder.getAvailableProviders()
          availableProviders.forEachIndexed { idx, provider ->
            if (idx > 0) {
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
        }.gap(RightGap.SMALL)
        enableCheckbox = enableCheckboxCell.component
        enableCheckbox.addActionListener {
          enableButtonAction()
        }
        infoRow.visibleIf(enableCheckbox.selected.not())
        userDropDownLink = DropDownLink<UserProviderHolder?>(userProviderHolder) { link: DropDownLink<UserProviderHolder?>? -> showAccounts(link) }
        cellDropDownLink = cell(userDropDownLink).onChangedContext { component, context ->
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
        }.comment("", MAX_LINE_LENGTH_NO_WRAP)
      }.visibleIf(userAccountListIsNotEmpty)

      // settings to sync
      rowsRange {
        group(message("enable.dialog.select.what.to.sync")) {
          row {
            val icon = JLabel(AllIcons.General.BalloonWarning)
            val textPanel = JBHtmlPane().apply {
              text = ""
              isEditable = false
              isOpaque = false
              addHyperlinkListener {
                if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                  val syncTypeDialog = ChangeSyncTypeDialog(configPanel, enableSyncOption.get())
                  if (syncTypeDialog.showAndGet()) {
                    enableSyncOption.set(syncTypeDialog.option)
                  }
                }
              }
            }.also {
              syncTypeLabel = it
              enableSyncOption.afterChange {
                updateSyncOptionText()
              }
            }
            val panel = RoundedBorderLayoutPanel(
              hgap = 8,
              vgap = 0,
              borderColor = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR,
              backgroundColor = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND,
              borderOffset = 8,
            ).apply {
              addToLeft(icon)
              addToCenter(textPanel)
            }
            cell(panel).resizableColumn().align(AlignX.FILL).customize(UnscaledGaps(8))
          }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL).visibleIf(remoteSettingsExist)

          row {
            cell(syncConfigPanel)
              .onReset {
                syncConfigPanel.reset()
                wasUsedBefore.set(currentUser() != null)
                hasMultipleProviders.set(RemoteCommunicatorHolder.getExternalProviders().isNotEmpty())
                enableCheckbox.isSelected = SettingsSyncSettings.getInstance().syncEnabled
                if (currentUser() != null) {
                  userDropDownLink.selectedItem = userAccountsList.firstOrNull { it.userId == SettingsSyncLocalSettings.getInstance().userId}
                } else if (userAccountListIsNotEmpty.get()) {
                   userDropDownLink.selectedItem = userAccountsList.firstOrNull { it != UserProviderHolder.addAccount }
                } else {
                  userDropDownLink.selectedItem = null
                }
                syncStatusChanged()
              }
              .onIsModified {
                enableCheckbox.isSelected != SettingsSyncSettings.getInstance().syncEnabled
                || syncConfigPanel.isModified()
                || (SettingsSyncLocalSettings.getInstance().userId != null && userDropDownLink.selectedItem?.userId != SettingsSyncLocalSettings.getInstance().userId)
              }
              .onApply {
                with(SettingsSyncLocalSettings.getInstance()) {
                  userId = userDropDownLink.selectedItem?.userId
                  providerCode = userDropDownLink.selectedItem?.providerCode
                }
                cellDropDownLink.comment?.text = ""
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

      }.visibleIf(userAccountListIsNotEmpty)

      // apply necessary changes
    }
    syncStatusChanged()
    return configPanel
  }

  private fun handleDisableSync() {
    SettingsSyncSettings.getInstance().syncEnabled = false
    when (disableSyncOption.get()) {
      DisableSyncType.DISABLE_AND_REMOVE_DATA -> {
        disableAndRemoveData()
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(
          SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_AND_REMOVED_DATA_FROM_SERVER)
      }
      DisableSyncType.DISABLE -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
      else -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
    }
    disableSyncOption.set(DisableSyncType.DISABLE)
    syncStatusChanged()
  }

  private fun enableButtonAction(){
    // enableCheckbox state here has already changed, so we react to it
    if (enableCheckbox.isSelected) {
      val pendingUserAction = getPendingUserAction()
      if (pendingUserAction != null) {
        refreshActionRequired()
        return
      }
      runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("enable.sync.check.server.data.progress")) {
        val (userId, userData, providerCode, providerName) = userDropDownLink.selectedItem ?: run {
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
          cellDropDownLink.comment?.text = "<icon src='AllIcons.General.History'>&nbsp;" +
                                           message("sync.status.will.enable",
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
        cellDropDownLink.comment?.text = message("sync.status.will.disable")
        handleDisableSync()
      } else {
        enableCheckbox.isSelected = true
      }
    } else {
      syncStatusChanged()
    }
  }

  private fun showDisableSyncDialog(): DisableSyncType {
    val providerName = userDropDownLink.selectedItem?.providerName ?: ""
    val intResult = if (SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired) {
      Messages.showDialog(
        message("disable.dialog.text", providerName),
        message("disable.dialog.title"),
        arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
        1,
        1,
        Messages.getQuestionIcon(), null
      )
    } else {
      Messages.showCheckboxMessageDialog(
        message("disable.dialog.text", providerName),
        message("disable.dialog.title"),
        arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
        message("disable.dialog.remove.data.box", providerName),
        false,
        1,
        1,
        Messages.getQuestionIcon()
      ) { index: Int, checkbox: JCheckBox ->
        if (index == 1) {
          if (checkbox.isSelected) 2 else 1
        }
        else {
          0
        }
      }
    }
    return DisableSyncType.entries.find { it.value == intResult } ?: DisableSyncType.DONT_DISABLE
  }

  private fun disableCurrentSyncDialog() : Boolean {
    val disableSyncConfirmed = yesNo(message("disable.active.sync.title"), message("disable.active.sync.message"))
      .yesText(message("disable.dialog.disable.button"))
      .noText(CommonBundle.getCancelButtonText())
      .guessWindowAndAsk()
    if (disableSyncConfirmed) {
      enableCheckbox.isSelected = false
      disableSyncOption.set(DisableSyncType.DISABLE)
      handleDisableSync()
    }
    return disableSyncConfirmed
  }

  private fun disableAndRemoveData() {
    try {
      val result = runWithModalProgressBlocking(ModalTaskOwner.component(configPanel), message("disable.remove.data.title"), TaskCancellation.cancellable()) {
        withTimeoutOrNull(60_000) {
          removeRemoteData()
        }
      }
      when (result) {
        null -> {
          val timeoutMessage = "Remote data removal timed out after 60 seconds"
          LOG.warn(timeoutMessage)
          lastRemoveRemoteDataError = timeoutMessage
          syncStatusChanged()
        }
        is DeleteServerDataResult.Error -> {
          LOG.warn("Failed to remove server data: ${result.error}")
          lastRemoveRemoteDataError = result.error
          syncStatusChanged()
        }
        DeleteServerDataResult.Success -> {
          syncStatusChanged()
        }
      }
    } catch (ex: CancellationException) {
      LOG.info("Remote data removal was cancelled")
      throw ex
    } catch (ex: Exception) {
      LOG.warn("Unexpected error during server data removal: ${ex.message}", ex)
      lastRemoveRemoteDataError = ex.message
      syncStatusChanged()
    }
  }

  private suspend fun removeRemoteData(): DeleteServerDataResult {
    val result = suspendCancellableCoroutine { continuation ->
      SettingsSyncEvents.getInstance().fireSettingsChanged(
        SyncSettingsEvent.DeleteServerData { deleteResult ->
          continuation.resume(deleteResult) { _, _, _ -> }
        }
      )
    }
    return result
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
    val html = HtmlBuilder().apply {
      append(HtmlChunk.div().addText(message))
      append(HtmlChunk.div()
       .style("margin-top: 5px")
       .child(HtmlChunk.link("", message("enable.dialog.change")))
      )
    }.wrapWithHtmlBody()
    syncTypeLabel.text = html.toString()
  }

  private fun showAccounts(link: DropDownLink<UserProviderHolder?>?): JBPopup {
    val accounts = object : BaseListPopupStep<UserProviderHolder>() {
      private var stepSelectedValue: UserProviderHolder? = null
      override fun onChosen(selectedValue: UserProviderHolder, finalChoice: Boolean): PopupStep<*>? {
        stepSelectedValue = selectedValue
        return FINAL_CHOICE
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
          text = "<a>${message("logout.link.text", provider.authService.providerName ?: "")}</a>"

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
    if (selectedValue == UserProviderHolder.addAccount) {
      if (SettingsSyncSettings.getInstance().syncEnabled && !disableCurrentSyncDialog()) {
        return
      }
      val syncTypeDialog = AddAccountDialog(configPanel)
      if (syncTypeDialog.showAndGet()) {
        val providerCode = syncTypeDialog.providerCode
        val provider = RemoteCommunicatorHolder.getProvider(providerCode) ?: return
        login(provider, syncConfigPanel)
      }
    }
    else {
      val wasEnabled = SettingsSyncSettings.getInstance().syncEnabled
      if (enableCheckbox.isSelected) {
        if (wasEnabled) {
          if (!disableCurrentSyncDialog()) {
            return
          }
          userDropDownLink.selectedItem = selectedValue
          enableCheckbox.doClick()
        } else {
          userDropDownLink.selectedItem = selectedValue
          enableButtonAction()
        }
      } else {
        userDropDownLink.selectedItem = selectedValue
      }
    }
  }


  private fun updateUserAccountsList() {
    userAccountsList.clear()
    val providersList = RemoteCommunicatorHolder.getAvailableProviders()
    providersList.forEach { communicator ->
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
    userAccountListIsNotEmpty.set(userAccountsList.any {  it != UserProviderHolder.addAccount })
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
            val remoteCommunicator = RemoteCommunicatorHolder.createRemoteCommunicator(provider, userData.id, loginDisposable) ?: return@withContext
            if (checkServerState(syncPanelHolder, remoteCommunicator, provider.authService.crossSyncSupported())) {
              SettingsSyncEvents.getInstance().fireLoginStateChanged()
              userDropDownLink.selectedItem = UserProviderHolder(userData.id, userData, provider.authService.providerCode,
                                                                 provider.authService.providerName, null)
              enableCheckbox.isSelected = true
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
      finally {
        Disposer.dispose(loginDisposable)
      }
      syncConfigPanel.requestFocusInWindow()
    }
  }

  private fun SettingsSyncUserData.toUserProviderHolder(providerName: String, separatorString: String? = null) =
    UserProviderHolder(id, this, providerCode, providerName, separatorString)

  override fun syncStatusChanged() {
    if (!::cellDropDownLink.isInitialized)
      return
    updateUserAccountsList()
    refreshActionRequired()
    if (!enableCheckbox.isSelected) {
      if (lastRemoveRemoteDataError != null) {
        cellDropDownLink.comment?.text =  "<icon src='AllIcons.General.Error'>&nbsp;" +
          message("disable.remove.data.failure", lastRemoveRemoteDataError!!)
      } else {
        cellDropDownLink.comment?.text = ""
      }
      return
    }
    if (SettingsSyncSettings.getInstance().syncEnabled) {
      val currentStatus = SettingsSyncStatusTracker.getInstance().currentStatus
      if (currentStatus == SettingsSyncStatusTracker.SyncStatus.Success) {
        val lastSyncTime = SettingsSyncStatusTracker.getInstance().getLastSyncTime()
        if (lastSyncTime > 0) {
          cellDropDownLink.comment?.text = "<icon src='AllIcons.General.GreenCheckmark'>&nbsp;" + message("sync.status.last.sync.message", DateFormatUtil.formatPrettyDateTime(lastSyncTime))
        }
        else {
          cellDropDownLink.comment?.text = message("sync.status.enabled")
        }
      }
      else if (currentStatus is SettingsSyncStatusTracker.SyncStatus.Error) {
        cellDropDownLink.comment?.text = message("sync.status.failed", currentStatus.errorMessage)
      }
    }
    else {
      //statusLabel.icon = icons.SettingsSyncIcons.StatusNotRun
      cellDropDownLink.comment?.text = ""
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
      cellDropDownLink.comment?.text = message("sync.status.action.required.comment",
                                               userActionRequired.actionTitle,
                                               userActionRequired.actionDescription ?: userActionRequired.message)
    }
    else {
      cellDropDownLink.comment?.text = ""
      actionRequiredAction = null
      actionRequiredLabel.text = ""
      actionRequiredButton.text = ""
    }
  }

  private fun getPendingUserAction(): PendingUserAction? {
    if (!enableCheckbox.isSelected)
      return null
    return userDropDownLink.selectedItem?.let {
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

  private enum class DisableSyncType(val value: Int) {
    DISABLE(1),
    DISABLE_AND_REMOVE_DATA(2),
    DONT_DISABLE(0)
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
              text(message("enable.dialog.source.option.title")).applyToComponent {
                font = JBFont.h3()
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
      arrayOf(cancelAction, okAction)
  }

  private class AddAccountDialog(parent: JComponent) : DialogWrapper(parent, false) {

    var providerCode: String = ""

    init {
      title = message("title.settings.sync")
      init()
    }

    override fun createContentPaneBorder(): Border {
      val insets = JButton().insets
      return JBUI.Borders.empty(14, 20, 20 - insets.bottom, 20 - insets.right)
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
          panel {
            row {
              text(message("enable.sync.choose.data.provider.title")).applyToComponent {
                font = JBFont.h3()
              }
            }
            val availableProviders = RemoteCommunicatorHolder.getAvailableProviders().filter { it.isAvailable() }
            availableProviders.firstOrNull { it.learnMoreLinkPair2 != null }?.also {
              row {
                val linkPair = it.learnMoreLinkPair2!!
                browserLink(linkPair.first, linkPair.second)
              }
            }

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
              val providerPanel = createRadioButtonPanelForProvider(provider, buttonGroup)
              radioButtonPanel.add(providerPanel)
            }
            row {
              cell(radioButtonPanel)
            }
          }
        }
      }
    }

    private fun createRadioButtonPanelForProvider(provider: SettingsSyncCommunicatorProvider, buttonGroup: ButtonGroup): JPanel {
      val radioButton = JBRadioButton().apply {
        actionCommand = provider.providerCode
        addActionListener {
          if (isSelected) {
            providerCode = provider.providerCode
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

      val iconLabel = provider.authService.icon?.let { JLabel(it).apply { addMouseListener(mouseListener) } }
      val textLabel = JLabel(provider.authService.providerName).apply { addMouseListener(mouseListener) }
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