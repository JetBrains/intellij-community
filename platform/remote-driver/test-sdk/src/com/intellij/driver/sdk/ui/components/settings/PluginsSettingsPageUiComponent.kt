package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.boundsOnScreen
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.common.tabbedPane
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.waitSelected
import com.intellij.driver.sdk.ui.xQuery
import java.awt.Point
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
  step("Open the Plugin Manager by invoking the action") {
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
  x("${xQuery { byType("com.intellij.ide.plugins.newui.PluginSearchTextField") }}/ancestor::div[.//div[@accessiblename='Installed' and @javaclass='javax.swing.JLabel']][1]", PluginsSettingsPageUiComponent::class.java).apply {
  }.apply(action)

class PluginsSettingsPageUiComponent(data: ComponentData) : UiComponent(data) {
  val searchPluginTextField = textField { byAccessibleName("Search plugins") }
  val installedTab = x { and(byType(JLabel::class.java), byAccessibleName("Installed")) }
  val marketplaceTab = x { and(byType(JLabel::class.java), byAccessibleName("Marketplace")) }
  val gearButton = x { byAccessibleName("Manage Repositories, Configure Proxy or Install Plugin from Disk") }
  val searchOptionsButton = x { byAccessibleName("Search Options") }

  fun waitLoaded(timeout: Duration = 40.seconds) {
    waitFor("no progress indicators on plugins page", timeout) {
      xx { byType("com.intellij.util.ui.AsyncProcessIcon") }.list().isEmpty()
    }
  }

  fun openSettingsPopup() {
    x("//div[@myicon='settings.svg']").click()
  }

  fun installedTabWorkaroundApply() {
    // workaround for bug https://youtrack.jetbrains.com/issue/IJPL-228318 - switch between tabs to refresh UI state
    openInstalledTab()
    openMarketplaceTab()
  }

  fun openInstalledTab(): PluginsSettingsPageUiComponent {
    step("Go to the Installed tab") {
      installedTab.click()
    }
    return this
  }

  fun openMarketplaceTab(): PluginsSettingsPageUiComponent {
    step("Go to the Marketplace tab") {
      marketplaceTab.click()
    }
    return this
  }

  fun searchForPlugin(pluginName: String): PluginsSettingsPageUiComponent {
    step("Search for '$pluginName'") {
      searchPluginTextField.text = pluginName
    }
    return this
  }

  fun clearPluginsSearchField(): PluginsSettingsPageUiComponent {
    step("Click on 'Clear all' button") {
      searchPluginTextField.run {
        val bounds = boundsOnScreen
        click(Point(bounds.width - 20, bounds.height / 2))
      }
    }
    return this
  }

  fun PluginsSettingsPageUiComponent.waitForPluginInList(pluginName: String, timeout: Duration = 10.seconds): ListPluginComponent {
    return step("Wait for plugin '$pluginName' to appear in the list") {
      listPluginComponent(pluginName).waitFound(timeout)
    }
  }

  fun getPluginsList(): List<ListPluginComponent> =
    xx("//*[@javaclass='com.intellij.ide.plugins.newui.ListPluginComponent']", ListPluginComponent::class.java).list()

  fun listPluginComponent(pluginName: String, action: ListPluginComponent.() -> Unit = {}): ListPluginComponent =
    x(ListPluginComponent::class.java) {
      and(byType("com.intellij.ide.plugins.newui.ListPluginComponent"), byAccessibleName(pluginName))
    }.apply(action)

  fun pluginDetailsPage(action: PluginDetailsPage.() -> Unit = {}): PluginDetailsPage =
    x(PluginDetailsPage::class.java) { byType("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }.apply(action)

  //Dirty kostyl because our plugin manager can show this btn in one of these places or in both
  // and we cant predict it until full refactoring
  fun checkAnyRestartBtnForPlugin(pluginName: String) {
    step("Wait for restart button to appear either in list or in details") {
      waitFor("restart button to appear", 2.minutes) {
        listPluginComponent(pluginName).xx { byAccessibleName("Restart IDE") }.list().isNotEmpty() ||
        pluginDetailsPage().xx { byType("com.intellij.ide.plugins.newui.RestartButton") }.list().isNotEmpty()
      }
    }
  }

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

    fun installPlugin(): ListPluginComponent {
      step("Wait for Install btn and click") {
        installButton.waitFound(10.seconds).click()
      }
      return this
    }

    fun updatePlugin(): ListPluginComponent {
      step("Wait for update btn and click") {
        updateButton.waitFound(30.seconds).click()
      }
      return this
    }

    fun checkInstalledBtn(): ListPluginComponent {
      step("Wait for the Installed btn appearance") {
        installedButton.waitFound(2.minutes)
      }
      return this
    }

    fun checkRestartBtnInPluginsList(): ListPluginComponent {
      step("Wait for the Restart IDE btn appearance in the plugins list") {
        restartIdeButton.waitFound(2.minutes)
      }
      return this
    }

    fun checkUiElementsForEnabledPlugin(): ListPluginComponent {
      step("Check UI elements for enabled plugin") {
        enabledCheckBox.waitFound(30.seconds)
        enabledCheckBox.waitSelected(true)
      }
      return this
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

    fun checkRestartBtn(): PluginDetailsPage {
      step("Check the Restart IDE in the plugins description") {
        restartButton.waitFound(2.minutes)
      }
      return this
    }

    class OptionButtonUiComponent(data: ComponentData) : UiComponent(data) {
      val disableButton = x { and(byType(JButton::class.java), byAccessibleName("Disable")) }
      val enableButton = x { and(byType(JButton::class.java), byAccessibleName("Enable")) }
    }
  }
}

//TODO made extension from WindowUiComponent on fixing remDev tests
fun IdeaFrameUI.shutdownDialog(accessibleName: String, action: RemDevShutdownDialog.() -> Unit = {}): RemDevShutdownDialog =
  x(RemDevShutdownDialog::class.java) { and(byType(JDialog::class.java), byAccessibleName(accessibleName)) }.apply(action)

class RemDevShutdownDialog(data: ComponentData) : DialogUiComponent(data) {
  val postponeButton = x { and(byType(JButton::class.java), byText("Cancel")) }
  val shutdownButton = x { and(byType(JButton::class.java), byAccessibleName("Shutdown")) }
}

fun IdeaFrameUI.restartDialog(action: RestartDialog.() -> Unit = {}): RestartDialog =
  x(RestartDialog::class.java) { and(byType(JDialog::class.java), byAccessibleName("IntelliJ IDEA and Plugin Updates")) }
    //x("//div[@accessiblename='IntelliJ IDEA and Plugin Updates']/ancestor::div[@javaclass='javax.swing.JPanel'][1]", RestartDialog::class.java).apply(action)

class RestartDialog(data: ComponentData) : DialogUiComponent(data) {
  val restartIdeButton = x { and(byType(JButton::class.java), or(byText("Restart"), byText("Shutdown"))) }
  val postponeButton = x { and(byType(JButton::class.java), byText("Not Now")) }

  fun postponeRestart() {
    step("Click Not now button in Restart IDE dialog") {
      postponeButton.click()
    }
  }

  fun restart() {
    step("Click Restart IDE button in Restart IDE dialog") {
      restartIdeButton.click()
    }
  }
}
