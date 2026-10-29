package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.WindowUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import java.awt.Window
import javax.swing.JTree
import kotlin.time.Duration.Companion.minutes

fun Finder.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}): WelcomeScreenUI {
  val nonModalWelcomeScreenEnabled = driver.withContext {
    val componentsLoaded = utility(LoadingStateRef::class, rdTarget = RdTarget.BACKEND).valueOf("COMPONENTS_LOADED")
    waitFor("Backend components are loaded", timeout = 1.minutes) {
      componentsLoaded.isOccurred()
    }
    new(
      WelcomeAccessPointEpRef::class,
      "com.intellij.platform.ide.welcomeScreenAccessPoint",
      rdTarget = RdTarget.BACKEND,
    ).getExtensionsIfPointIsRegistered().singleOrNull()?.isAvailable() == true
  }

  if (nonModalWelcomeScreenEnabled) {
    return nonModalWelcomeScreen(action)
  }
  val welcomeScreenClass = if (isRemDevMode) "TabbedWelcomeScreen" else "FlatWelcomeFrame"
  return x("//div[@class='${welcomeScreenClass}']", WelcomeScreenUI::class.java).apply(action)
}

fun Driver.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}): WelcomeScreenUI {
  return this.ui.welcomeScreen(action)
}

fun Finder.nonModalWelcomeScreen(action: NonModalWelcomeScreenUI.() -> Unit): NonModalWelcomeScreenUI {
  return x(NonModalWelcomeScreenUI::class.java) {
    componentWithChild(byType(Window::class.java), byType("com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftToolbarPanel"))
  }.apply(action)
}

fun Driver.nonModalWelcomeScreen(action: NonModalWelcomeScreenUI.() -> Unit = {}): NonModalWelcomeScreenUI {
  return this.ui.nonModalWelcomeScreen(action)
}

open class WelcomeScreenUI(data: ComponentData) : WindowUiComponent(data) {
  open val createNewProjectButton: UiComponent = x("//div[(@accessiblename='New Project' and @class='JButton') or (@visible_text='New Project' and @class!='JBLabel')]")
  open val openProjectButton: UiComponent = x("//div[(@accessiblename='Open' and @class='JButton')  or (@visible_text='Open' and @class!='JBLabel')]")
  open val fromVcsButton: UiComponent = x("//div[@accessiblename='Clone Repository' and @class='JButton']")

  private val leftItems: JTreeUiComponent = tree("//div[@accessiblename='Welcome screen categories']")

  fun openSettingsDialog() {
    driver.invokeAction("ShowSettings", now = false)
  }

  fun clickProjects() {
    openSection("Projects")
  }

  fun clickCustomize() {
    openSection("Customize")
  }

  open fun clickPlugins() {
    openSection("Plugins")
  }

  fun clickLearn() {
    openSection("Learn")
  }

  open fun clickRecentProject(projectName: String) {
    x { byClass("CardLayoutPanel") }.waitOneText(projectName).click()
  }

  open fun createNewProject() {
    createNewProjectButton.click()
  }

  protected open fun openSection(name: String) {
    leftItems.clickPath(name)
  }
}

class NonModalWelcomeScreenUI(data: ComponentData) : WelcomeScreenUI(data) {
  override val createNewProjectButton: UiComponent = disclosureButton("New")
  override val openProjectButton: UiComponent = disclosureButton("Open")
  override val fromVcsButton: UiComponent = disclosureButton("Clone Repository")

  val recentProjectsTree: JTreeUiComponent = accessibleTree { and(byType(JTree::class.java), byAccessibleName("Recent Projects")) }

  val homeButton: UiComponent = x { and(byType("com.intellij.openapi.wm.impl.ToolbarComboButton"), contains(byAccessibleName("Home"))) }

  val openFileButton: UiComponent = disclosureButton("Open File")
  val openTerminalButton: UiComponent = disclosureButton("Open Terminal")

  val pluginsButton: UiComponent = x { and(byType("com.intellij.openapi.actionSystem.impl.ActionButtonWithText"), byAccessibleName("Plugins")) }
  val themeButton: UiComponent = x { and(byType("com.intellij.openapi.wm.impl.ToolbarComboButton"), contains(byAccessibleName("Theme:"))) }
  val keymapButton: UiComponent = x { and(byType("com.intellij.openapi.wm.impl.ToolbarComboButton"), contains(byAccessibleName("Keymap:"))) }

  override fun clickRecentProject(projectName: String) {
    recentProjectsTree.clickPath(projectName, fullMatch = false)
  }

  override fun createNewProject() {
    createNewProjectButton.click()
    popup().accessibleList().clickItem("Project…")
  }

  override fun clickPlugins() {
    pluginsButton.click()
  }

  override fun openSection(name: String) {
    waitFound()
  }

  private fun disclosureButton(accessibleName: String): UiComponent = x { and(byType("com.intellij.ui.components.DisclosureButton"), byAccessibleName(accessibleName)) }
}

@Remote("com.intellij.diagnostic.LoadingState")
private interface LoadingStateRef {
  fun valueOf(name: String): LoadingStateRef
  fun isOccurred(): Boolean
}

@Remote("com.intellij.openapi.extensions.ExtensionPointName")
private interface WelcomeAccessPointEpRef {
  fun getExtensionsIfPointIsRegistered(): List<WelcomeAccessPointRef>
}

@Remote("com.intellij.ide.welcomeScreen.WelcomeAccessPoint")
private interface WelcomeAccessPointRef {
  fun isAvailable(): Boolean
}
