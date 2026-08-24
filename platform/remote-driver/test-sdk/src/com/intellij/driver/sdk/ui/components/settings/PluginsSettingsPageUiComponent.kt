package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.CountIcon
import com.intellij.driver.sdk.PluginDescriptor
import com.intellij.driver.sdk.PluginId
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.boundsOnScreen
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JBTabbedPaneUiComponent
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.common.tabbedPane
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextComponentUI
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.fileChooser
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.table
import com.intellij.driver.sdk.ui.components.elements.textComponent
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.waitSelected
import com.intellij.driver.sdk.ui.hasFocus
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.openapi.util.SystemInfo
import java.awt.Point
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.pluginsSettingsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage(action = action)

fun SettingsDialogUiComponent.pluginsSettingsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage().apply(action)

fun WelcomeScreenUI.pluginsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  onPluginsPage().apply(action)

fun Driver.openPluginsSettings() {
  step("Open the Plugin Manager by invoking the action") {
    invokeAction("ShowPlugins", now = false)
  }
}

private fun Finder.onPluginsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  x("${xQuery { byType("com.intellij.ide.plugins.newui.PluginSearchTextField") }}/ancestor::div[.//div[@accessiblename='Installed' and @javaclass='javax.swing.JLabel']][1]",
    PluginsSettingsPageUiComponent::class.java).apply {
  }.apply(action)


abstract class LoadablePluginsUiComponent(data: ComponentData) : UiComponent(data) {
  val progressIcons: UIComponentsList<UiComponent> = xx { byType("com.intellij.util.ui.AsyncProcessIcon") }

  fun waitLoaded(timeout: Duration = 40.seconds) {
    waitFor("Expected NO progress indicators", timeout) {
      progressIcons.list().isEmpty()
    }
  }
}

class PluginsSettingsPageUiComponent(data: ComponentData) : LoadablePluginsUiComponent(data) {

  val searchPluginTextField: JTextFieldUI = textField("Plugins search textfield") { byAccessibleName("Search plugins") }
  val installedTab: JLabelUiComponent =
    x(JLabelUiComponent::class.java, readableName = "Installed tab") { and(byType(JLabel::class.java), byAccessibleName("Installed")) }
  val marketplaceTab: UiComponent = x("Marketplace tab") { and(byType(JLabel::class.java), byAccessibleName("Marketplace")) }
  val gearButton: UiComponent = x("Gear button") { byAccessibleName("Manage Repositories, Configure Proxy or Install Plugin from Disk") }
  val searchOptionsButton: UiComponent = x("Search filters") { byAccessibleName("Search Options") }
  val updateAllButton: UiComponent = x("Update All link") { byAccessibleName("Update all") }
  val pluginsList: UIComponentsList<ListPluginComponent> get() = xx("//*[@javaclass='com.intellij.ide.plugins.newui.ListPluginComponent']", ListPluginComponent::class.java)

  //todo change to open gear method in rd tests
  fun openSettingsPopup() {
    x("//div[@myicon='settings.svg']", readableName = "Settings (gear) icon").click()
  }

  fun installedTabWorkaroundApply() {
    // TODO workaround for bug https://youtrack.jetbrains.com/issue/IJPL-228318 - switch between tabs to refresh UI state
    openInstalledTab()
    openMarketplaceTab()
  }

  fun openInstalledTab(): PluginsSettingsPageUiComponent {
    step("Go to the Installed tab") {
      installedTab.click()
      waitLoaded()
    }
    return this
  }

  fun openMarketplaceTab(): PluginsSettingsPageUiComponent {
    step("Go to the Marketplace tab") {
      marketplaceTab.click()
    }
    waitLoaded()
    return this
  }

  fun openGearSettingsPopup(): GearSettingsPopup {
    step("Click on Gear icon and open settings popup") {
      gearButton.click()
    }
    return driver.ui.gearSettingsPopup()
  }

  fun getUpdatesCountIndicator(): CountIcon? {
    val icon = installedTab.getIcon() ?: return null
    return driver.cast(icon, CountIcon::class)
  }

  fun getPluginsUpdateCount(): Int {
    return getUpdatesCountIndicator()?.getText()?.toIntOrNull() ?: 0
  }

  fun searchForPlugin(pluginName: String): PluginsSettingsPageUiComponent {
    step("Search for '$pluginName'") {
      searchPluginTextField.waitFound()
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

  fun waitForPluginInList(pluginName: String, timeout: Duration = 10.seconds): ListPluginComponent {
    return step("Wait for plugin '$pluginName' to appear in the list") {
      getPluginFromList(pluginName).waitFound(timeout)
    }
  }

  fun getPluginsList(): List<ListPluginComponent> =
    pluginsList.list()

  fun getNewPluginsSection(): List<ListPluginComponent> =
    xx(
      "//*[@accessiblename='New and Updated']" +
      "/following-sibling::*[@javaclass='com.intellij.ide.plugins.newui.ListPluginComponent']",
      ListPluginComponent::class.java
    ).list()

  fun getPluginFromList(pluginName: String, action: ListPluginComponent.() -> Unit = {}): ListPluginComponent =
    x(ListPluginComponent::class.java, readableName = "Plugin '$pluginName' in a list") {
      and(byType("com.intellij.ide.plugins.newui.ListPluginComponent"), byAccessibleName(pluginName))
    }.apply(action)

  fun isPluginPresentInList(pluginName: String): Boolean =
    getPluginFromList(pluginName).present()

  fun pluginDetailsPage(action: PluginDetailsPage.() -> Unit = {}): PluginDetailsPage =
    x(PluginDetailsPage::class.java, "Plugins description page") { byType("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }.apply(action)

  //TODO Dirty kostyl because our plugin manager can show this btn in one of these places or in both
  // and we cant predict it until full refactoring
  fun checkAnyRestartBtnForPlugin(pluginName: String) {
    step("Wait for restart button to appear either in list or in details") {
      waitFor(errorMessage = { "Restart button isn't present neither in plugins list nor in plugin description" }, timeout = 2.minutes) {
        getPluginFromList(pluginName).restartIdeButton.present() ||
        pluginDetailsPage().restartButtonDesc.present()
      }
    }
  }

  class ListPluginComponent(data: ComponentData) : UiComponent(
    data.copy(readableName = data.readableName ?: "Element in a list of plugins")) {
    private val listPluginComponent get() = driver.cast(component, ListPluginComponentRef::class)

    val name: String? get() = accessibleName
    val installButton: JButtonUiComponent =
      button("'Install' button") { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val installedButton: UiComponent =
      x("Installed button") { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val uninstalledButton: UiComponent =
      x("Uninstalled button") { and(byType(JButton::class.java), byAccessibleName("Uninstalled")) }
    val enabledCheckBox: JCheckBoxUi = checkBox("State checkbox") { and(byType(JCheckBox::class.java), byAccessibleName("Enabled")) }
    val ultimateTagLabel: UiComponent = x("'Ultimate' label") { and(byType("com.intellij.ide.plugins.newui.TagComponent"), byAccessibleName("Ultimate")) }
    val proTagLabel: UiComponent = x("'Pro' label") { and(byType("com.intellij.ide.plugins.newui.TagComponent"), byAccessibleName("Pro")) }
    val errorNotice: JTextComponentUI = textComponent("Error notice") { byType("com.intellij.ide.plugins.newui.ErrorComponent") }
    val unknownUpdateSourceWarning: UiComponent = x("Unknown update source warning") { byType("com.intellij.ui.components.JBTextArea") }
    val updatePluginButton: UiComponent = x("Update button") { byAccessibleName("Update") }
    val restartIdeButton: UiComponent = x("Restart button") { byAccessibleName("Restart IDE") }

    fun getPluginDescriptor(): PluginDescriptor =
      checkNotNull(listPluginComponent.getPluginModel().getDescriptor()) { "Plugin $name not found" }

    fun getPluginId(): String =
      getPluginDescriptor().getPluginId().getIdString()

    fun installPlugin(): ListPluginComponent {
      step("Wait for Install btn and click") {
        installButton.click()
      }
      return this
    }

    fun updatePlugin(): ListPluginComponent {
      step("Wait for update plugin btn and click") {
        updatePluginButton.click()
      }
      return this
    }

    fun checkInstalledBtn(): ListPluginComponent {
      step("Wait for the Installed btn appearance") {
        installedButton.waitFound()
      }
      return this
    }

    fun checkUninstalledBtn(): ListPluginComponent {
      step("Wait for the Uninstalled btn appearance") {
        uninstalledButton.waitFound()
      }
      return this
    }

    fun checkRestartBtnInPluginsList(timeout: Duration = 20.seconds): ListPluginComponent {
      step("Wait for the Restart IDE btn appearance in the plugins list") {
        restartIdeButton.waitFound(timeout)
      }
      return this
    }

    fun checkUiElementsForEnabledPlugin(): ListPluginComponent {
      step("Check UI elements for enabled plugin") {
        enabledCheckBox.waitSelected(true)
      }
      return this
    }

    fun checkPluginStateInUI(enabled: Boolean, timeout: Duration = 15.seconds) {
      step("Check plugin state checkbox in the UI") {
        enabledCheckBox.should("Plugin '$name': State checkbox should be ${if (enabled) "enabled" else "disabled"}", timeout) {
          isSelected() == enabled
        }
      }
    }

    fun checkErrorContainsText(errorText: String): ListPluginComponent {
      step("[Check] The error  notice in the Plugins list contains text '$errorText'") {
        errorNotice.waitContainsText(errorText)
      }
      return this
    }

    fun clickEnableRequiredPluginLink(): ListPluginComponent {
      step("Click the Enable required plugin link in the plugin error notice") {
        waitFor(timeout = 30.seconds) {
          errorNotice.text.contains("Enable required plugin")
        }
        errorNotice.clickText("Enable required plugin")
      }
      return this
    }

    fun uninstallPluginByHotkey(): ListPluginComponent {
      step("Press hotkey to uninstall plugin") {
        keyboard { key(if (SystemInfo.isMac) KeyEvent.VK_BACK_SPACE else KeyEvent.VK_DELETE) }
      }
      step("Confirm plugin uninstallation in the dialog") {
        driver.ui.dialog(title = "Uninstall Plugin?") {
          button("Yes").click()
        }
      }
      return this
    }

    fun pluginDescription(): PluginDetailsPage {
      step("Open the plugin description page") {
        this.click()
      }
      return driver.ui.x(PluginDetailsPage::class.java) { byType("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }
    }
  }

  @Remote("com.intellij.ide.plugins.newui.ListPluginComponent")
  interface ListPluginComponentRef {
    fun getPluginModel(): PluginUiModel
  }

  @Remote("com.intellij.ide.plugins.newui.PluginUiModel")
  interface PluginUiModel {
    val pluginId: PluginId
    fun getDescriptor(): PluginDescriptor
  }

  class PluginDetailsPage(data: ComponentData) : LoadablePluginsUiComponent(data) {
    val optionButton: OptionButtonUiComponent = x(OptionButtonUiComponent::class.java) { byType("com.intellij.ide.plugins.newui.buttons.OptionButton") }
    val installButton: UiComponent = x("Install button") { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val installOptionButton: UiComponent = x("Install btn in plugin description") { byType("com.intellij.ide.plugins.newui.buttons.InstallOptionButton") }
    val restartButtonDesc: UiComponent = x("Restart btn in plugin description") { byType("com.intellij.ide.plugins.newui.RestartButton") }
    val uninstallButton: UiComponent = x("Uninstall btn in plugin description") { and(byType(JButton::class.java), byAccessibleName("Uninstall")) }
    val installedButton: UiComponent = x("Installed btn in plugin description") { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val disableButton: UiComponent = x("Disable btn in plugin description") { and(or(byClass("JButton"), byClass("MainButton")), byAccessibleName("Disable")) }
    val enableButton: UiComponent = x("Enable btn in plugin description") { and(or(byType(JButton::class.java), byClass("MainButton")), byAccessibleName("Enable")) }
    val updateButton: UiComponent = x("Update btn in plugin description") { and(or(byType(JButton::class.java), byClass("MainButton")), byAccessibleName("Update")) }
    val arrowButton: UiComponent =
      x("Uninstall dropdown") { byType($$"com.intellij.ui.components.BasicOptionButtonUI$ArrowButton") }
    val restartIdeButton: UiComponent = x("Restart button") { byAccessibleName("Restart IDE") }

    val tabbedPane: JBTabbedPaneUiComponent = tabbedPane()
    val overviewTab: UiComponent = tabbedPane.tab("Overview")
    val whatsNewTab: UiComponent = tabbedPane.tab("What's New")
    val reviewsTab: UiComponent = tabbedPane.tab("Reviews")
    val additionalInfoTab: UiComponent = tabbedPane.tab("Additional Info")
    val updateSourceValue: UiComponent =
      x("${xQuery { and(byType(JLabel::class.java), byText("Updates from:")) }}/following-sibling::div[1]")
    val updateSourceBanners: UIComponentsList<UpdateSourceBannerUiComponent> =
      xx(UpdateSourceBannerUiComponent::class.java) { byType("com.intellij.ide.plugins.newui.UpdateSourceBanner") }
    val versionPanel: UiComponent = x { byType("com.intellij.ide.plugins.newui.VersionPanel") }
    val pluginHomepage: UiComponent = x("Plugin homepage link") { byAccessibleName("Plugin homepage") }

    fun hasUnknownUpdateSourceWarningBanner(): Boolean = updateSourceBanners.list().any { it.isWarning() }
    fun hasUpdateSourceSetBanner(): Boolean = updateSourceBanners.list().any { it.isSuccess() }

    fun additionalText(text: String): UiComponent = x { and(byType(JLabel::class.java), byText(text)) }

    fun chooseUpdateSourceFromWarningBanner(updateSource: String): PluginDetailsPage {
      step("Choose '$updateSource' update source from warning banner") {
        updateSourceBanners.list().first { it.isWarning() }.chooseUpdateSourceAction.click()
        driver.ui.popup().list().clickItem(updateSource)
      }
      return this
    }

    fun chooseUpdateSourceFromDescription(updateSource: String): PluginDetailsPage {
      step("Choose '$updateSource' update source from `Update from` description") {
        check(tabbedPane.selectedTabName == additionalInfoTab.accessibleName) {
          "Tab \'${additionalInfoTab.accessibleName}\' is not selected; selected tab\'${tabbedPane.selectedTabName}\'"
        }
        updateSourceValue.click()
        driver.ui.popup().list().clickItem(updateSource)
      }
      return this
    }

    fun updatePlugin(): PluginDetailsPage {
      step("Click on 'Update' button in the plugin description") {
        updateButton.click()
      }
      waitLoaded(20.seconds)
      return this
    }


    fun uninstallPlugin(): PluginDetailsPage {
      step("Click on dropdown and uninstall plugin") {
        arrowButton.click()
        driver.ui.popup().accessibleList().clickItem("Uninstall")
      }
      return this
    }

    class OptionButtonUiComponent(data: ComponentData) : UiComponent(data) {
      val disableButton: UiComponent = x { and(or(byType(JButton::class.java), byClass("MainButton")), byAccessibleName("Disable")) }
      val enableButton: UiComponent = x { and(or(byType(JButton::class.java), byClass("MainButton")), byAccessibleName("Enable")) }
    }

    class UpdateSourceBannerUiComponent(data: ComponentData) : UiComponent(data) {
      private val banner get() = driver.cast(component, UpdateSourceBannerRef::class)

      val chooseUpdateSourceAction: UiComponent = x(xQuery { byVisibleText("Choose where to get updates") })

      fun isWarning(): Boolean = banner.getStatus().name() == "Warning"
      fun isSuccess(): Boolean = banner.getStatus().name() == "Success"
    }

    @Remote("com.intellij.ide.plugins.newui.UpdateSourceBanner")
    interface UpdateSourceBannerRef {
      fun getStatus(): EditorNotificationPanelStatusRef
    }

    @Remote($$"com.intellij.ui.EditorNotificationPanel$Status")
    interface EditorNotificationPanelStatusRef {
      fun name(): String
    }
  }
}

//TODO made extension from WindowUiComponent on fixing remDev tests
fun IdeaFrameUI.shutdownDialog(accessibleName: String, action: RemDevShutdownDialog.() -> Unit = {}): RemDevShutdownDialog =
  x(RemDevShutdownDialog::class.java) { and(byType(JDialog::class.java), byAccessibleName(accessibleName)) }.apply(action)

class RemDevShutdownDialog(data: ComponentData) : DialogUiComponent(data) {
  val postponeButton: UiComponent = x("Postpone restart button") { and(byType(JButton::class.java), byText("Cancel")) }
  val shutdownButton: UiComponent = x("Shutdown button") { and(byType(JButton::class.java), byAccessibleName("Shutdown")) }
}

fun IdeaFrameUI.restartDialog(action: RestartDialog.() -> Unit = {}): RestartDialog =
  x(RestartDialog::class.java) { and(byType(JDialog::class.java), byAccessibleName("IntelliJ IDEA and Plugin Updates")) }


class RestartDialog(data: ComponentData) : DialogUiComponent(data) {
  val restartIdeButton: UiComponent =
    x("Restart button") { and(byType(JButton::class.java), or(byText("Restart"), byText("Shutdown"))) }
  val postponeButton: UiComponent = x("Not Now button") { and(byType(JButton::class.java), byText("Not Now")) }

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

fun PluginsSettingsPageUiComponent.installationErrorDialog(action: DialogUiComponent.() -> Unit = {}): DialogUiComponent =
  x(DialogUiComponent::class.java, readableName = "Installation error dialog") {
    contains(byAccessibleName("download or installation failed"))
  }.apply(action)


//this dialog is a separate component, not a part of settings window
fun Finder.customRepositoriesDialog(action: CustomPluginsRepositoryListDialog.() -> Unit = {}): CustomPluginsRepositoryListDialog =
  x(CustomPluginsRepositoryListDialog::class.java, readableName = "Custom repositories list dialog") {
    byTitle("Custom Plugin Repositories")
  }.apply(action)

//this popup is a separate component, not a part of settings window
fun Finder.gearSettingsPopup(action: GearSettingsPopup.() -> Unit = {}): GearSettingsPopup =
  x(GearSettingsPopup::class.java, readableName = "Plugins gear settings popup") {
    componentWithChild(byClass("HeavyWeightWindow"), byType(JList::class.java))
  }.apply(action)


class GearSettingsPopup(data: ComponentData) : PopupUiComponent(data) {

  fun addCustomPluginRepo(repositoryUrl: String) {
    step("Click on 'Manage Plugin Repositories' item") {
      accessibleList().clickItem("Manage Plugin Repositories…")
    }
    driver.ui.customRepositoriesDialog().addCustomRepository(repositoryUrl)
  }

  fun installPluginFromDisk(pluginZipPath: Path) {
    step("Click on 'Install Plugin from Disk' item") {
      accessibleList().clickItem("Install Plugin from Disk")
    }
    step("Choose plugin zip file in the File Chooser") {
      driver.ui.fileChooser({ byAccessibleName("Choose Plugin File") }).openPath(pluginZipPath)
    }
  }
}

class CustomPluginsRepositoryListDialog(data: ComponentData) : DialogUiComponent(data) {
  val addRepoBtn: UiComponent = x("Add custom repository button") { byAccessibleName("Add") }

  // 'Add' inserts an empty row and starts its cell editor, which is an ExtendableTextField focused asynchronously
  private val repoUrlField: JTextFieldUI =
    textField("Custom repository url field") { byType("com.intellij.ui.components.fields.ExtendableTextField") }

  fun addCustomRepository(repoUrl: String) {
    step("Add custom plugin repository '$repoUrl'") {
      addRepoBtn.click()
      table().should("cell editor of the table with custom repos should be focused") { driver.hasFocus(this) }
      repoUrlField.text = repoUrl
      okButton.click()
    }
  }
}
