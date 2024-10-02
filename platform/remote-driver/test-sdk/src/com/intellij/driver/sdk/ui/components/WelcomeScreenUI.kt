package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.*

fun Finder.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}): WelcomeScreenUI {
  val welcomeScreenClass = if (isRemoteIdeMode) "TabbedWelcomeScreen" else "FlatWelcomeFrame"
  return x("//div[@class='${welcomeScreenClass}']", WelcomeScreenUI::class.java).apply(action)
}

fun Driver.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}) = this.ui.welcomeScreen(action)

class WelcomeScreenUI(data: ComponentData) : UiComponent(data) {
  val createNewProjectButton = x("//div[(@accessiblename='New Project' and @class='JButton') or (@visible_text='New Project' and @class!='JBLabel')]")
  val openProjectButton = x("//div[(@accessiblename='Open' and @class='JButton')  or (@visible_text='Open' and @class!='JBLabel')]")
  val fromVcsButton = x("//div[@accessiblename='Clone Repository' and @class='JButton']")

  private val leftItems = tree("//div[@class='Tree']")

  fun clickProjects() = leftItems.clickPath("Projects")
  fun clickRemoteDev() = leftItems.clickPath("Remote Development")
  fun clickRemoteDevSsh() = leftItems.clickPath("Remote Development", "SSH")
  fun clickRemoteDevSpace() = leftItems.clickPath("Remote Development", "JetBrains Space")
  fun clickCustomize() = leftItems.clickPath("Customize")
  fun clickPlugins() = leftItems.clickPath("Plugins")
  fun clickLearn() = leftItems.clickPath("Learn")
  fun clickRecentProject(projectName: String) {
    x { byClass("CardLayoutPanel") }.waitOneText(projectName).click()
  }
}