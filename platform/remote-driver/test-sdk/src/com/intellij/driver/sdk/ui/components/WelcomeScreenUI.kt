package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.*

fun Finder.welcomeScreen(action: WelcomeScreenUI.() -> Unit) {
  x("//div[@class='FlatWelcomeFrame']", WelcomeScreenUI::class.java).action()
}

class WelcomeScreenUI(data: ComponentData) : UiComponent(data) {
  val createNewProjectButton = x("//div[(@accessiblename='New Project' and @class='JButton') or (@visible_text='New Project' and @class!='JBLabel')]")
  val openProjectButton = x("//div[@accessiblename='Open or Import' and @class='JButton']")
  val fromVcsButton = x("//div[@accessiblename='Get from Version Control...' and @class='JButton']")

  private val leftItems = tree("//div[@class='Tree']")

  fun clickProjects() = leftItems.clickPath("Projects")
  fun clickRemoteDev() = leftItems.clickPath("Remote Development")
  fun clickRemoteDevSsh() = leftItems.clickPath("Remote Development", "SSH")
  fun clickRemoteDevSpace() = leftItems.clickPath("Remote Development", "JetBrains Space")
  fun clickCustomize() = leftItems.clickPath("Customize")
  fun clickPlugins() = leftItems.clickPath("Plugins")
  fun clickLearn() = leftItems.clickPath("Learn")
}