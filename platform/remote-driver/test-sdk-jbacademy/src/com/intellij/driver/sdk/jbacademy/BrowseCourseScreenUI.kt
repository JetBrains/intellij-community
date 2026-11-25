package com.intellij.driver.sdk.jbacademy

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.browseCourseScreen(action: BrowseCourseScreenUI.() -> Unit = {}): BrowseCourseScreenUI {
  val selectCourseScreenClass = "MyDialog"
  return x("//div[@class='${selectCourseScreenClass}']", BrowseCourseScreenUI::class.java).apply(action)
}

open class BrowseCourseScreenUI(data: ComponentData) : UiComponent(data) {
  val eduPlatformList
    get() = x("//div[contains(@visible_text, 'My Courses')]")

  val createCourseLink
    get() = x("//div[@class='HyperlinkLabel' and contains(@visible_text, 'Create course...')]")

  //All Courses || Marketplace || Hyperskill || Coursera || CheckiO || Codeforces || My Courses
  fun clickMarketplace() {
    eduPlatformList.waitOneText("Marketplace").click()
  }
}

fun Finder.eduCourseCard(courseName: String = "AtomicKotlin"): EduCourseCard {
  return x("//div[@class='EduCourseCard'][.//div[@text='$courseName']]", type = EduCourseCard::class.java)
}

open class EduCourseCard(data: ComponentData) : UiComponent(data) {}

fun Finder.marketplaceCoursePanel(): MarketplaceCoursePanel {
  return x("//div[@class='MarketplaceCoursePanel']", type = MarketplaceCoursePanel::class.java)
}

class MarketplaceCoursePanel(data: ComponentData) : UiComponent(data) {

  val sdkWarnings
    get() = x("//div[contains(@visible_text, 'Learn how to configure')]")

  val startCourseButton
    get() = x("//div[@class='StartCourseButton']")

  val openCourseButton
    get() = x("//div[@class='OpenCourseButton']")

  val settingsPanel
    get() = x("//div[@class='CourseSettingsPanel']")

  val toolchainLocationField
  get() = x("//div[@text='Add SDK…']")
}

fun Finder.userAgreement(action: JBAUserAgreementDialog.() -> Unit = {}): JBAUserAgreementDialog {
  return x("//div[@title='JetBrains Academy Plugin User Agreement']", JBAUserAgreementDialog::class.java).apply(action)
}

open class JBAUserAgreementDialog(data: ComponentData) : UiComponent(data) {
  val agreeButton
    get() = x("//div[@class='JButton' and @text='Agree']")

  val cancelButton
    get() = x("//div[@class='DialogPanel']/div[@class='JButton' and @text='Cancel']")

}