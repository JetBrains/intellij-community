@file:Suppress("DialogTitleCapitalization", "HardCodedStringLiteral", "UnstableApiUsage")

package com.intellij.grazie.ide.ui.widget

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.cloud.GrazieCloudConnectionState
import com.intellij.grazie.cloud.GrazieCloudConnectionState.ConnectionState
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.cloud.license.GrazieLoginManager
import com.intellij.grazie.cloud.license.GrazieLoginState
import com.intellij.grazie.cloud.license.launchDisposable
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.icons.ModifiedGrazieIcons
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable
import com.intellij.grazie.ide.ui.configurable.writingStyleComboBox
import com.intellij.grazie.ide.ui.proofreading.ProofreadConfigurable
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.popup.PopupState
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.event.ItemEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane

private val logger = logger<GrazieStatusBarWidget>()

class GrazieStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
  private val widgetComponent by lazy { createComponent() }
  private val popupState = PopupState.forPopup()

  private val messageBusConnection = application.messageBus.connect(this)

  private fun createComponent(): IconLabelButton {
    val button = IconLabelButton(GrazieIcons.Stroke.Grazie) {
      if (!popupState.isRecentlyHidden) {
        // Have to recreate the popup each time because it gets disposed on close :(
        createAndShowPopup(widgetComponent)
      }
    }

    fun updatePresentation() {
      val (icon, text) = obtainWidgetPresentation()
      button.icon = icon
      button.toolTipText = text
    }
    GrazieCloudConnector.subscribeToAuthorizationStateEvents(this) { updatePresentation() }
    messageBusConnection.subscribe(GrazieCloudConnectionState.Listener.TOPIC, GrazieCloudConnectionState.Listener { updatePresentation() })

    // Defer initial update to avoid cycle when GrazieConfig is still initializing
    application.invokeLater {
      updatePresentation()
      GrazieConfig.subscribe(this) { updatePresentation() }
    }
    return button
  }

  private fun computeLoginStatePresentation(): Pair<Icon, String>? {
    return when (GrazieLoginManager.lastState) {
      GrazieLoginState.WaitingForJba -> ModifiedGrazieIcons.Refresh to GrazieBundle.message("grazie.status.bar.login.status.waiting.for.jba")
      GrazieLoginState.WaitingForCloud -> ModifiedGrazieIcons.Refresh to GrazieBundle.message("grazie.status.bar.login.status.waiting.for.cloud")
      GrazieLoginState.WaitingForLicense -> ModifiedGrazieIcons.Refresh to GrazieBundle.message("grazie.status.bar.login.status.waiting.for.license")
      else -> null
    }
  }

  private fun obtainWidgetPresentation(): Pair<Icon, String> {
    val loginStatePresentation = computeLoginStatePresentation()
    if (loginStatePresentation != null) {
      return loginStatePresentation
    }
    if (GrazieConfig.get().processing == Processing.Cloud) {
      val state = GrazieCloudConnectionState.obtainCurrentState()
      if (state == ConnectionState.Stable && GrazieCloudConnector.isAuthorized()) {
        val msg =
          if (isEnterprise()) GrazieBundle.message("grazie.status.bar.widget.tooltip.text.connected.enterprise")
          else GrazieBundle.message("grazie.status.bar.widget.tooltip.text.connected")
        return GrazieIcons.Stroke.GrazieCloudProcessing to msg
      }
      if (state == ConnectionState.Error) {
        return GrazieIcons.Stroke.GrazieCloudError to GrazieBundle.message("grazie.status.bar.widget.tooltip.text.connection.error")
      }
    }
    return GrazieIcons.Stroke.Grazie to GrazieBundle.message("grazie.status.bar.widget.tooltip.text.local")
  }

  private fun isEnterprise(): Boolean = GrazieLoginManager.lastState is GrazieLoginState.Enterprise

  private fun createAndShowPopup(component: JComponent) {
    val disposable = Disposer.newCheckedDisposable()
    val mainContentPanel = createPopupComponent(disposable)
    val contentComponent = Box(BoxLayout.Y_AXIS)
    contentComponent.add(mainContentPanel)
    val builder = JBPopupFactory.getInstance().createComponentPopupBuilder(contentComponent, null)
    builder.setCancelOnOtherWindowOpen(true)
    builder.setCancelOnClickOutside(true)
    builder.setShowBorder(false)
    val popup = builder.createPopup()
    Disposer.register(popup, disposable)
    messageBusConnection.subscribe(GrazieCloudConnectionState.Listener.TOPIC, GrazieCloudConnectionState.Listener {
      adjustPopupDimensionsAndPosition()
    })
    popup.pack(true, true)
    popupState.prepareToShow(popup)
    popup.showAboveOnTheLeft(component)
    GrazieScope.coroutineScope().launchDisposable(parent = this) {
      GrazieLoginManager.state().collectLatest {
        // Launch on the next AWT event to make sure the adjustment will happen after every child component is updated
        launch(context = Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
          adjustPopupDimensionsAndPosition()
        }
      }
    }
  }

  @RequiresEdt
  private fun adjustPopupDimensionsAndPosition() {
    val popup = popupState.popup ?: return
    popup.pack(true, true)
    popup.adjustLocation(component)
  }

  private fun createPopupComponent(disposable: Disposable): DialogPanel {
    val content = panel {
      popupHeaderRow()
      loginAndProcessingRow(disposable).bottomGap(BottomGap.SMALL)
      writingStyleRow()
    }
    val border = JBUI.Borders.empty(8, 20)
    return content.withBorder(border).withBackground(JBUI.CurrentTheme.Popup.BACKGROUND)
  }

  private fun Panel.popupHeaderRow(): Row {
    return row {
      @Suppress("DialogTitleCapitalization")
      label(GrazieBundle.message("grazie.status.bar.widget.header.text")).bold()
      actionButton(OpenGrazieSettingsAction(), ActionPlaces.STATUS_BAR_PLACE).align(AlignX.RIGHT)
    }
  }

  private fun Panel.writingStyleRow(): Row {
    return row(GrazieBundle.message("grazie.status.bar.widget.writing.style.label.text")) {
      writingStyleComboBox().bindItemWithImmediateApply(
        getter = { GrazieConfig.get().getTextStyle() },
        setter = { value ->
          GrazieConfig.update {
            return@update it.copy(styleProfile = value.id)
          }
        }
      ).apply {
        gap(RightGap.SMALL)
        widthGroup(comboBoxWidthGroup)
        columns(COLUMNS_SHORT)
      }
      actionsButtonWithoutDropdownIcon(
        ConfigureInGrazieAction(),
        icon = AllIcons.Actions.More
      )
    }
  }

  private fun Panel.loginAndProcessingRow(disposable: Disposable): Row {
    val isCloudProcessingSelected = isCloudProcessingSelected(disposable)
    val isAuthorized = isAuthorized(disposable)
    val hasEnterpriseError = hasEnterpriseError(disposable)
    return rowGroup {
      processingRowGroup(disposable)
      panel(visibleIf = isCloudProcessingSelected and !isAuthorized and !hasEnterpriseError) {
        connectToGrazieCloudRow()
      }
    }
  }

  private fun Panel.processingRowGroup(disposable: Disposable): Panel {
    return processingRow(
      disposable = disposable,
      enableCloudLink = { enableCloudLink() },
      cloudComment = {
        comment(
          comment = GrazieBundle.message("grazie.status.bar.widget.cloud.comment.text"),
          maxLineLength = maxLineLength
        )
      },
      localComment = {
        comment(
          comment = GrazieBundle.message("grazie.status.bar.widget.connecting.comment"),
          maxLineLength = maxLineLength
        )
      }
    )
  }

  private fun Row.enableCloudLink(): Cell<ActionLink> {
    return link(GrazieBundle.message("grazie.status.bar.widget.enable.cloud.link.text")) {
      GrazieConfig.update { it.copy(explicitlyChosenProcessing = Processing.Cloud) }
      adjustPopupDimensionsAndPosition()
    }
  }

  private fun Panel.connectToGrazieCloudRow(): Row {
    return row {
      button(text = GrazieBundle.message("grazie.connect.to.cloud.button.text")) {
        if (!GrazieCloudConnector.askUserConsentForCloud()) return@button
        logger.debug { "Connect to Grazie Cloud button started from widget" }
        ApplicationManager.getApplication().runWriteIntentReadAction(ThrowableComputable {
          GrazieCloudConnector.connect(project)
        })
      }.apply {
        applyToComponent {
          defaultButton()
        }
      }
      rowComment(GrazieBundle.message("grazie.status.bar.widget.cloud.comment.text"), maxLineLength = maxLineLength)
    }
  }

  override fun getComponent(): JComponent {
    return widgetComponent
  }

  override fun ID(): String {
    return "GrazieStatusBarWidget"
  }

  override fun dispose(): Unit = Unit

  private fun Panel.processingRow(
    disposable: Disposable,
    enableCloudLink: Row.() -> Cell<ActionLink>,
    cloudComment: Row.() -> Cell<JEditorPane>,
    localComment: Row.() -> Cell<JEditorPane>,
  ): Panel {
    return panel {
      val hasEnterpriseError = hasEnterpriseError(disposable)
      panel(visibleIf = hasEnterpriseError) {
        aiEnterpriseError(disposable, localComment)
      }
      val isCloudConnectionStable = isCloudConnectionStable(disposable)
      panel(visibleIf = !isCloudConnectionStable and !hasEnterpriseError) {
        connectingLanguageProcessing(localComment)
      }
      val isLocalSelected = !isCloudProcessingSelected(disposable)
      val isCloudConnected = isAuthorized(disposable)
      val isWaitingForLogin = isWaitingForLogin(disposable)
      row(label = GrazieBundle.message("grazie.status.bar.widget.language.processing.label.text")) {
        panel(visibleIf = isLocalSelected) {
          row {
            icon(GrazieIcons.Stroke.Grazie).gap(RightGap.SMALL)
            label(GrazieBundle.message("grazie.status.bar.widget.local.processing.label.text")).gap(RightGap.SMALL)
            enableCloudLink().gap(RightGap.SMALL)
          }
        }
        panel(visibleIf = !isLocalSelected) {
          row(visibleIf = isWaitingForLogin) {
            waitingForLoginStateLabel(disposable).gap(RightGap.SMALL)
          }
          panel(visibleIf = !isWaitingForLogin) {
            either(
              isLeft = !isCloudConnected,
              left = {
                row {
                  icon(ModifiedGrazieIcons.ForcedLocal).apply {
                    gap(RightGap.SMALL)
                    applyToComponent {
                      toolTipText = GrazieBundle.message("grazie.status.bar.widget.cloud.not.accessible.text")
                    }
                  }
                  label(GrazieBundle.message("grazie.status.bar.widget.local.processing.label.text")).gap(RightGap.SMALL)
                }
              },
              right = {
                row {
                  icon(GrazieIcons.GrazieCloudProcessing).gap(RightGap.SMALL)
                  label(GrazieBundle.message("grazie.status.bar.widget.cloud.processing.label.text")).gap(RightGap.SMALL)
                }
              }
            )
          }
        }
      }.visibleIf(isCloudConnectionStable)
      row(visibleIf = isLocalSelected and isCloudConnected and !isWaitingForLogin) {
        cloudComment()
      }
    }
  }

  private fun Panel.aiEnterpriseError(disposable: Disposable, localComment: Row.() -> Cell<JEditorPane>) {
    panel {
      row {
        val error = label("")
        GrazieLoginManager.subscribeWithState(disposable) {
          error.component.text = (it as? GrazieLoginState.Enterprise)?.state?.error.orEmpty()
        }
      }
      row {
        localComment()
      }
    }
  }

  private fun Panel.connectingLanguageProcessing(localComment: Row.() -> Cell<JEditorPane>): Panel {
    return panel {
      row(GrazieBundle.message("grazie.status.bar.widget.language.processing.label.text")) {
        cell(AsyncProcessIcon("")).gap(RightGap.SMALL)
        text(GrazieBundle.message("grazie.status.bar.widget.cloud.connection.text"))
      }
      row {
        localComment()
      }
    }
  }

  /**
   * Mark the button to be shown as default (only for its look).
   */
  private fun JButton.defaultButton(): JButton {
    ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    return this
  }

  private open inner class OpenSettingsAction(
    private val dialogOpener: (project: Project?) -> Unit,
    text: @Nls String? = null,
    description: @Nls String? = null,
    icon: Icon? = AllIcons.General.Settings,
  ) : DumbAwareAction(text, description, icon) {
    override fun actionPerformed(event: AnActionEvent) {
      val project = event.getData(CommonDataKeys.PROJECT)
      popupState.popup?.cancel()
      dialogOpener(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private inner class OpenGrazieSettingsAction : OpenSettingsAction(
    dialogOpener = { ShowSettingsUtilImpl.showSettingsDialog(it, ProofreadConfigurable.ID, "") }
  )

  private inner class ConfigureInGrazieAction : OpenSettingsAction(
    dialogOpener = { StyleConfigurable.open(it) },
    text = GrazieBundle.message("grazie.status.bar.widget.configure.writing.style.action.text"),
    description = GrazieBundle.message("grazie.status.bar.widget.configure.writing.style.action.description"),
    icon = null
  )

  @Suppress("ConstPropertyName")
  companion object {
    private const val comboBoxWidthGroup = "ComboBoxWidthGroup"
    private const val maxLineLength = 39

    private val dialogPanelRightGap = IntelliJSpacingConfiguration().dialogUnscaledGaps.right
    private const val popupVerticalOffset = 13
    private val popupHorizontalOffset = dialogPanelRightGap

    private fun JBPopup.adjustLocation(component: JComponent) {
      adjustLocation(component, popupVerticalOffset, popupHorizontalOffset)
    }

    private fun JBPopup.showAboveOnTheLeft(component: JComponent) {
      showAboveOnTheLeft(component, popupVerticalOffset, popupHorizontalOffset)
    }
  }

  private fun Row.waitingForLoginStateLabel(disposable: Disposable): Panel {
    return panel {
      row {
        cell(AsyncProcessIcon(null)).gap(RightGap.SMALL)
        text(text = GrazieBundle.message("grazie.status.bar.login.status.waiting.for.cloud")).visibleIf(isLoginState(disposable, GrazieLoginState.WaitingForCloud))
        text(text = GrazieBundle.message("grazie.status.bar.login.status.waiting.for.jba")).visibleIf(isLoginState(disposable, GrazieLoginState.WaitingForJba))
        text(text = GrazieBundle.message("grazie.status.bar.login.status.waiting.for.license")).visibleIf(isLoginState(disposable, GrazieLoginState.WaitingForLicense))
      }
    }
  }

  /**
   * Binds [getter] and [setter] to the combo box item and applies its changes to the model immediately via [setter].
   *
   * * __Does not consider cell enabled state__
   * * Sets `onReset` for updating the selected item on reset
   * * Sets `onIsModified` for consistency
   * * Does not set `onApply` to prevent multiple model updates with the same value.
   */
  @Suppress("UNCHECKED_CAST")
  private fun <T, C : ComboBox<T>> Cell<C>.bindItemWithImmediateApply(getter: () -> T, setter: (T) -> Unit): Cell<C> {
    component.selectedItem = getter()
    onReset { component.selectedItem = getter() }
    onIsModified { component.selectedItem == getter() }
    component.addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        val item = event.item as T
        setter(item)
      }
    }
    return this
  }

  private fun isAuthorized(disposable: Disposable): ComponentPredicate = LoginStatePredicate(disposable) { it.isCloudConnected }

  private fun isCloudProcessingSelected(disposable: Disposable): ComponentPredicate {
    return IsCloudProcessingSelectedPredicate(disposable)
  }

  private fun isCloudConnectionStable(disposable: Disposable): ComponentPredicate {
    return IsCloudConnectionStablePredicate(disposable)
  }

  private class IsCloudProcessingSelectedPredicate(private val disposable: Disposable) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      GrazieConfig.subscribe(disposable) {
        listener(it.processing == Processing.Cloud)
      }
    }

    override fun invoke(): Boolean = GrazieConfig.get().processing == Processing.Cloud
  }

  private class IsCloudConnectionStablePredicate(private val disposable: Disposable) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      val connection = application.messageBus.connect(disposable)
      connection.subscribe(GrazieCloudConnectionState.Listener.TOPIC, GrazieCloudConnectionState.Listener {
        listener(it == ConnectionState.Stable)
      })
    }

    override fun invoke(): Boolean = GrazieCloudConnectionState.obtainCurrentState() == ConnectionState.Stable
  }

  private fun isLoginState(disposable: Disposable, state: GrazieLoginState): ComponentPredicate {
    return LoginStatePredicate(disposable) { it == state }
  }

  private fun hasEnterpriseError(disposable: Disposable): ComponentPredicate =
    LoginStatePredicate(disposable) { it is GrazieLoginState.Enterprise && it.state.error != null }

  private fun isWaitingForLogin(disposable: Disposable): ComponentPredicate {
    val states = arrayOf(
      GrazieLoginState.WaitingForJba,
      GrazieLoginState.WaitingForCloud,
      GrazieLoginState.WaitingForLicense
    )
    return LoginStatePredicate(disposable) { it in states }
  }

  private class LoginStatePredicate(
    private val disposable: Disposable,
    private val predicate: (GrazieLoginState) -> Boolean,
  ) : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      GrazieScope.coroutineScope().launchDisposable(
        parent = disposable,
        context = Dispatchers.EDT + ModalityState.any().asContextElement()
      ) {
        GrazieLoginManager.state().collect { state -> listener(predicate(state)) }
      }
    }

    override fun invoke(): Boolean = predicate(GrazieLoginManager.lastState)
  }
}