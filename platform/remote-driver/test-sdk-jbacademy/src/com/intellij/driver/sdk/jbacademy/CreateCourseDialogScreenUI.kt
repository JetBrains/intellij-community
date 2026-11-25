package com.intellij.driver.sdk.jbacademy

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent


fun Finder.createCourseDialog(action: CreateCourseDialog.() -> Unit = {}): CreateCourseDialog {
  return x("//div[@accessiblename='New Course']", CreateCourseDialog::class.java).apply(action)
}

open class CreateCourseDialog(data: ComponentData) : UiComponent(data) {
  val courseNameField
    get() = x("//div[@accessiblename='Course name:' and @class='CourseTitleField']")

  val selectLanguageFieldComboBox
    get() = x("//div[@accessiblename='Language:' and @class='ComboBox']")

  val descriptionField
    get() = x("//div[@accessiblename='Description:' and @class='JBTextArea']")

  val settingsPanel
    get() = x(
      "//div[@visible_text='Settings']"
    )

  val leaveFeedbackLink
    get() = x("//div[@mytext='Leave feedback']"
    )

  val locationPathField
    get() = x("//div[@class='PathField']")

  val nextButton
    get() = x("//div[@text='Next']")

  // Second page of course creation dialog
  val secondPageTitle
    get() = x("//div[@accessiblename='New Course']")

  val secondPageTitleLabel
    get() = x("//div[@visible_text='Choose your first lesson type']")

  val secondPageSecondLabel
    get() = x("//div[@visible_text='You can add more lessons of any type later']")

  val createSimpleCourseCardButton
    get() = x("//div[@class='SimpleLessonCard']"
    )

  val simpleCourseCardTitle
    get() = x("//div[@visible_text='Simple lesson']"
    )

  val simpleCourseCardDescription
  get() = x("//div[@usertext='Students will work on a set of <i>independent</i> tasks to learn a particular topic']"
  )

  val createGuidedCourseCardButton
    get() = x("//div[@class='GuidedProjectCard']"
    )

  val guidedCourseCardTitle
    get() = x("//div[@visible_text='Guided project']"
    )

  val guidedCourseCardDescription
    get() = x("//div[@usertext='Students will work on a single project, gradually improving it through each of the interconnected tasks']"
    )

  // guidedProject, simpleLesson
  fun isCardSelected(courseType: String): Boolean {
    return x("//div[contains(@defaulticon, '${courseType}Selected.svg')]").present()
  }

  val createCourseButton
    get() = x("//div[@accessiblename='Create Course']"
    )

  val leaveFeedbackLinkButton
  get() = x("//div[@mytext='Leave feedback']"
    )

  val backButton
    get() = x("//div[@text='Back']")

  val cancelCourseButton
    get() = x("//div[@text='Cancel']")
}


