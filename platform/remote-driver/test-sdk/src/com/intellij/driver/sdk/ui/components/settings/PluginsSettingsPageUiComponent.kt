package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.common.tabbedPane
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.pluginsSettingsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage(action = action)

fun DialogUiComponent.pluginsSettingsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage().apply(action)

fun WelcomeScreenUI.pluginsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage().apply(action)

fun Driver.openPluginsSettings() {
  step("Open the Plugin Manager") {
    invokeAction("WelcomeScreen.Plugins", now = false)
  }
}

fun IdeaFrameUI.clickOkBtnAndCloseDialog() {
  step("Click the OK button and close the Plugin Manager") {
    settingsDialog {
      okButton.click()
    }
  }
}

private fun Finder.onPluginsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  x("${xQuery { byType("com.intellij.ide.plugins.newui.PluginSearchTextField") }}/ancestor::div[.//div[@accessiblename='Installed' and @javaclass='javax.swing.JLabel']][1]", PluginsSettingsPageUiComponent::class.java).apply(action)

fun IdeaFrameUI.shutdownDialog(accessibleName: String, action: YesNoDialog.() -> Unit = {}): YesNoDialog =
  x(YesNoDialog::class.java) { and(byType(JDialog::class.java), byAccessibleName(accessibleName)) }.apply(action)

class YesNoDialog(data: ComponentData) : UiComponent(data) {
  val cancelButton = x { and(byType(JButton::class.java), byText("Cancel")) }
  val shutdownButton = x { and(byType(JButton::class.java), byAccessibleName("Shutdown")) }
}

class PluginsSettingsPageUiComponent(data: ComponentData) : UiComponent(data) {
  val searchPluginTextField = textField { byAccessibleName("Search plugins") }
  val installedTab = x { and(byType(JLabel::class.java), byAccessibleName("Installed")) }
  val marketplaceTab = x { and(byType(JLabel::class.java), byAccessibleName("Marketplace")) }
  val gearButton = x { byAccessibleName("Manage Repositories, Configure Proxy or Install Plugin from Disk") }
  val searchOptionsButton = x { byAccessibleName("Search Options") }

  fun waitLoaded(timeout: Duration = 1.minutes) {
    waitFor("no progress indicators on plugins page", timeout) {
      xx { byType("com.intellij.util.ui.AsyncProcessIcon") }.list().isEmpty()
    }
  }

  fun openSettingsPopup() {
    x("//div[@myicon='settings.svg']").click()
  }

  fun listPluginComponent(pluginName: String, action: ListPluginComponent.() -> Unit = {}): ListPluginComponent =
    x(ListPluginComponent::class.java) {
      and(byType("com.intellij.ide.plugins.newui.ListPluginComponent"), byAccessibleName(pluginName))
    }.apply(action)

  fun getPluginsList(): List<ListPluginComponent> =
    xx("//*[@javaclass='com.intellij.ide.plugins.newui.ListPluginComponent']", ListPluginComponent::class.java).list()

  fun openInstalledTab() {
    step("Go to the Installed tab in the Plugin Manager") {
      installedTab.click()
    }
  }

  fun pluginDetailsPage(action: PluginDetailsPage.() -> Unit = {}): PluginDetailsPage =
    x(PluginDetailsPage::class.java) { byType("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }.apply(action)

  class ListPluginComponent(data: ComponentData) : UiComponent(data) {
    private val listPluginComponent get() = driver.cast(component, ListPluginComponentRef::class)

    val name get() = accessibleName
    val installButton = x { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val installedButton = x { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val enabledCheckBox = checkBox { and(byType(JCheckBox::class.java), byAccessibleName("Enabled")) }
    val ultimateTagLabel = x { and(byType("com.intellij.ide.plugins.newui.TagComponent"), byAccessibleName("Ultimate")) }
    val proTagLabel = x { and(byType("com.intellij.ide.plugins.newui.TagComponent"), byAccessibleName("Pro")) }
    val errorNotice = x { byType("com.intellij.ide.plugins.newui.ErrorComponent") }
    val updateButton = x { byAccessibleName("Update") }
    val restartIdeButton = x { byAccessibleName("Restart IDE") }
    val errorComponent = x { byType("com.intellij.ide.plugins.newui.ErrorComponent") }

    fun getPluginDescriptor(): PluginDescriptor =
      checkNotNull(driver.getPlugin(listPluginComponent.getPluginModel().pluginId.getIdString())) { "Plugin $name not found" }

    fun updatePlugin() {
      step("Wait for update btn and click") {
        updateButton.waitFound(30.seconds).click()
      }
    }

    fun checkRestartBtn() {
      step("Wait for the Restart IDE btn appearance") {
        restartIdeButton.waitFound(30.seconds)
      }
    }

    @Remote("com.intellij.ide.plugins.newui.ListPluginComponent")
    interface ListPluginComponentRef {
      fun getPluginModel(): PluginUiModel
    }

    @Remote("com.intellij.ide.plugins.newui.PluginUiModel")
    interface PluginUiModel {
      val pluginId: PluginId
    }
  }

  class PluginDetailsPage(data: ComponentData) : UiComponent(data) {
    val optionButton = x(OptionButtonUiComponent::class.java) { byType("com.intellij.ide.plugins.newui.buttons.OptionButton") }
    val installButton = x { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val installOptionButton = x { byType("com.intellij.ide.plugins.newui.buttons.InstallOptionButton") }
    val restartButton = x { byType("com.intellij.ide.plugins.newui.RestartButton") }
    val uninstallButton = x { and(byType(JButton::class.java), byAccessibleName("Uninstall")) }
    val installedButton = x { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val disableButton = x { and(byType(JButton::class.java), byAccessibleName("Disable")) }
    val enableButton = x { and(byType(JButton::class.java), byAccessibleName("Enable")) }
    val arrowButton = x { byType($$"com.intellij.ui.components.BasicOptionButtonUI$ArrowButton")}
    val restartIdeButton = x { byAccessibleName("Restart IDE") }
    val tabbedPane = tabbedPane()
    val overviewTab = tabbedPane.tab("Overview")
    val whatsNewTab = tabbedPane.tab("What's New")
    val reviewsTab = tabbedPane.tab("Reviews")
    val additionalInfoTab = tabbedPane.tab("Additional Info")
    val versionPanel = x { byType("com.intellij.ide.plugins.newui.VersionPanel") }

    fun additionalText(text: String): UiComponent = x { and(byType(JLabel::class.java), byText(text)) }

    class OptionButtonUiComponent(data: ComponentData) : UiComponent(data) {
      val disableButton = x { and(byType(JButton::class.java), byAccessibleName("Disable")) }
      val enableButton = x { and(byType(JButton::class.java), byAccessibleName("Enable")) }
    }
  }
}