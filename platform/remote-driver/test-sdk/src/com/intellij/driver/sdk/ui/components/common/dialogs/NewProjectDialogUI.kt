package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import javax.swing.JTextField
import kotlin.time.Duration.Companion.seconds

fun Finder.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  x("//div[@title='New Project' or @title='New Module']", NewProjectDialogUI::class.java).action()
}

fun Driver.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  this.ui.newProjectDialog(action)
}

open class NewProjectDialogUI(data: ComponentData) : UiComponent(data) {

  fun setProjectName(text: String) {
    nameTextField.text = text
  }

  fun specifyProjectGroup(text: String) {
    groupField.text = text
  }

  fun specifyArtifact(text: String) {
    artifactField.text = text
  }

  fun chooseProjectType(projectType: String) {
    projectTypeList.waitOneText(projectType).click()
  }

  fun selectJdk(jdkVersion: String) {
    step("Pick JDK version") {
      this.comboBox { byClass("ProjectWizardJdkComboBox") }
        .apply {
          val expectedItem = listValues().first { it.contains(jdkVersion) }
          selectItemContains(expectedItem)
        }
    }
  }

  fun specifyProjectLanguage(language: String) {
    x("//div[@text='Language:']/following-sibling::div[@class='SegmentedButtonComponent'][1]")
      .waitOneText(language)
      .click()
  }

  open fun chooseBuildSystem(buildSystem: String) {
    x("//div[@text='Build system:']/following-sibling::div[@class='SegmentedButtonComponent']")
      .waitOneText(buildSystem)
      .click()
  }

  open fun chooseGradleDsl(gradleDsl: String) {
    x("//div[@text='Gradle DSL:']/following-sibling::div[@class='SegmentedButtonComponent']")
      .waitOneText(gradleDsl)
      .click()
  }

  fun getBasePythonInterpreters(): List<String> {
    interpreterSelector.waitFound().shouldBe(
      message = "Wait for interpreter list to appear",
      timeout = 5.seconds,
      condition = { listValues().isNotEmpty() }
    )
    return interpreterSelector.listValues()
  }

  val nameTextField = textField { and(byAccessibleName("Name:"), byType(JTextField::class.java)) }
  val groupField = textField { and(byAccessibleName("Group:"), byType(JTextField::class.java)) }
  val artifactField = textField { and(byAccessibleName("Artifact:"), byType(JTextField::class.java)) }
  val nextButton = x("//div[@text='Next']")
  open val createButton = x("//div[@text='Create']")
  val cancelButton = x("//div[@text='Cancel']")
  private val projectTypeList = x("//div[@class='JBList']")
  val sampleCodeLabel = checkBox { byText("Add sample code") }
  val multiModuleLabel = checkBox { byText("Generate multi-module build") }
  val compactStructureLabel = checkBox { byText("Use compact project structure") }
  val createMainPyCheckbox = checkBox { byText("Create a welcome script") }
  val installMinicondaLink = x("//div[@text='Install Miniconda']")
  val projectVenvButton = x { byAccessibleName("Project venv") }
  private val interpreterSelector = comboBox { byClass("PythonInterpreterComboBox") }
}

