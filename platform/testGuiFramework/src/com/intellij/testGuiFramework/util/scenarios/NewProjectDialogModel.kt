// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonFinish
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonNext
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateFromArchetype
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateProjectFromTemplate
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkKotlinDsl
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupAndroid
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupApplicationForge
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupClouds
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupEmptyProject
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupFlash
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupGradle
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupGrails
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupGriffon
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupGroovy
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupIntelliJPlatformPlugin
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupJ2ME
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupJBoss
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupJava
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupJavaEnterprise
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupJavaFX
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupKotlin
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupMaven
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupNodeJs
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupSpring
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupSpringInitializr
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupStaticWeb
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.progressLoadingTemplates
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textArtifactId
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textBasePackage
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textGroupId
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textProjectLocation
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textProjectName
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Pause
import java.awt.Point
import java.io.Serializable

class NewProjectDialogModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<NewProjectDialogModel>(
    { NewProjectDialogModel(it) }
  )

  object Constants {
    // dialog & UI elements
    const val newProjectTitle = "New Project"
    const val buttonNext = "Next"
    const val buttonFinish = "Finish"
    const val checkCreateProjectFromTemplate = "Create project from template"
    const val textProjectName = "Project name:"
    const val textProjectLocation = "Project location:"
    const val textBasePackage = "Base package:"
    const val textGroupId = "GroupId"
    const val textArtifactId = "ArtifactId"
    const val checkKotlinDsl = "Kotlin DSL build script"
    const val checkCreateFromArchetype = "Create from archetype"
    const val comboProjectStructure = "Project structure:"
    const val textRootModuleName = "Root module name:"
    const val checkCreateJvmModule = "Create JVM module:"
    const val checkCreateJsModule = "Create JS module:"
    const val progressLoadingTemplates = "Loading Templates"
    const val textApplicationServer = "Application Server:"
    const val buttonOk = "OK"
    const val buttonCancel = "Cancel"
    const val progressSearchingForAppServerLibraries = "Searching for Application Server Libraries"
    const val buttonNew = "New..."

    // groups
    const val groupJava = "Java"
    const val groupJavaEnterprise = "Java Enterprise"
    const val groupJBoss = "JBoss"
    const val groupJ2ME = "J2ME"
    const val groupClouds = "Clouds"
    const val groupSpring = "Spring"
    const val groupJavaFX = "Java FX"
    const val groupAndroid = "Android"
    const val groupIntelliJPlatformPlugin = "IntelliJ Platform Plugin"
    const val groupSpringInitializr = "Spring Initializr"
    const val groupMaven = "Maven"
    const val groupGradle = "Gradle"
    const val groupGroovy = "Groovy"
    const val groupGriffon = "Griffon"
    const val groupGrails = "Grails"
    const val groupApplicationForge = "Application Forge"
    const val groupKotlin = "Kotlin"
    const val groupStaticWeb = "Static Web"
    const val groupNodeJs = "Node.js and NPM"
    const val groupFlash = "Flash"
    const val groupEmptyProject = "Empty Project"

    // libraries and frameworks
    const val libJava = "Java"
    const val libJBoss = "JBoss"
    const val libArquillianJUnit = "Arquillian JUnit"
    const val libArquillianTestNG = "Arquillian TestNG"
    const val libJBossDrools = "JBoss Drools"
    const val libWebApplication = "Web Application"
    const val itemKotlinMppDeprecated = "Kotlin (Multiplatform - Deprecated)"
    const val itemKotlinMppExperimental = "Kotlin (Multiplatform - Experimental)"
    const val itemKotlinMppLibrary = "Kotlin (Multiplatform Library)"
    const val itemKotlinMppClientServer = "Kotlin (JS Client/JVM Server)"
    const val itemKotlinMppMobileAndroidIos = "Kotlin (Mobile Android/iOS)"
    const val itemKotlinMppMobileSharedLibrary = "Kotlin (Mobile Shared Library)"
    const val itemKotlinJvm = "Kotlin/JVM"
    const val itemKotlinJs = "Kotlin/JS"
    const val itemKotlinNative = "Kotlin/Native"
    const val itemGradleKotlinJvm = "Kotlin (Java)"
    const val itemGradleKotlinJs = "Kotlin (JavaScript)"
  }

  enum class Groups(private val title: String) {
    Java(groupJava), JavaEnterprise(groupJavaEnterprise), JBoss(groupJBoss),
    J2ME(groupJ2ME), Clouds(groupClouds), Spring(groupSpring), JavaFX(groupJavaFX),
    Android(groupAndroid), IPPlugin(groupIntelliJPlatformPlugin), SpringInitializr(groupSpringInitializr),
    Maven(groupMaven), Gradle(groupGradle), Groovy(groupGroovy), Griffon(groupGriffon),
    Grails(groupGrails), ApplicationForge(groupApplicationForge), Kotlin(groupKotlin),
    StaticWeb(groupStaticWeb), NodeJs(groupNodeJs), Flash(groupFlash), Empty(groupEmptyProject)
    ;

    override fun toString() = title
  }

  enum class GradleGroupModules(val title: String) {
    ExplicitModuleGroups("using explicit module groups"),
    QualifiedNames("using qualified names");

    override fun toString() = title
  }

  enum class GradleOptions(val title: String) {
    UseAutoImport("Use auto-import"),
    GroupModules("Group Modules"),
    SeparateModules("Create separate module per source set");

    override fun toString() = title
  }

  data class GradleProjectOptions(
    val group: String = "gradleGroup",
    val artifact: String,
    val framework: String = "",
    val useKotlinDsl: Boolean = false,
    val isJavaShouldNotBeChecked: Boolean = false,
    val useAutoImport: Boolean = false,
    val useSeparateModules: Boolean = true,
    val groupModules: GradleGroupModules = GradleGroupModules.QualifiedNames)

  data class MavenProjectOptions(
    val group: String = "mavenGroup",
    val artifact: String,
    val useArchetype: Boolean = false,
    val archetypeGroup: String = "",
    val archetypeVersion: String = ""
  )

  enum class MppProjectStructure(private val title: String) {
    FlatStructure("Flat, all created modules on the same level"),
    HierarchicalStructure("Hierarchical, platform modules under common one")
    ;

    override fun toString() = title
  }

  class LibraryOrFramework(vararg val mainPath: String) : Serializable {

    override fun equals(other: Any?): Boolean {
      if (other == null) return false
      if (other !is LibraryOrFramework) return false
      return this.mainPath.contentEquals(other.mainPath)
    }

    override fun hashCode(): Int {
      val hashCodePrime = 31
      return mainPath.fold(1) { acc, s -> s.hashCode() * hashCodePrime + acc } * hashCodePrime
    }

    override fun toString(): String {
      fun Array<out String>.toFormattedString() = if (this.isEmpty()) ""
      else this.joinToString(separator = "-") { it.replace(",", "").replace(" ", "") }
      return mainPath.toFormattedString()
    }

    fun isEmpty() = mainPath.isEmpty() || mainPath.first().isEmpty()
  }
}

val GuiTestCase.newProjectDialogModel by NewProjectDialogModel

fun NewProjectDialogModel.connectDialog(): JDialogFixture =
  step("search New Project dialog") { testCase.dialog(NewProjectDialogModel.Constants.newProjectTitle, true) }

typealias LibrariesSet = Set<NewProjectDialogModel.LibraryOrFramework>

fun LibrariesSet.isSetEmpty() = isEmpty() || all { it.isEmpty() }
fun LibrariesSet.isSetNotEmpty() = !isSetEmpty()

/**
 * Creates a new project from Java group
 * @param projectPath - path where the project is going to be created
 * @param libs - path to additional library/framework that should be checked
 * Note: only one library/framework can be checked!
 * */
fun NewProjectDialogModel.createJavaProject(projectPath: String,
                                            projectSdk: String,
                                            libs: LibrariesSet = emptySet(),
                                            template: String = "",
                                            basePackage: String = "") {
  with(guiTestCase) {
    fileSystemUtils.assertProjectPathExists(projectPath)
    with(connectDialog()) {
      selectProjectGroup(NewProjectDialogModel.Groups.Java)
      if (projectSdk.isNotEmpty()) selectSdk(projectSdk)
      if (libs.isSetNotEmpty()) setLibrariesAndFrameworks(libs)
      else {
        button(buttonNext).click()
        if (template.isNotEmpty()) {
          waitForPageTransitionFinished {
            val cmp = checkbox(checkCreateProjectFromTemplate).target()
            logInfo("found component ${cmp.hashCode().toString(16)}")
              cmp.locationOnScreen
          }
          val templateCheckbox = checkbox(checkCreateProjectFromTemplate)
          if (!templateCheckbox.isSelected)
            templateCheckbox.click()
          jList(template).clickItem(template)
        }
      }
      button(buttonNext).click()
      typeProjectNameAndLocation(projectPath)
      if (template.isNotEmpty() && basePackage.isNotEmpty()) {
        // base package is set only for Command Line app template
        step("set 'Base package' to '$basePackage'") {
          textfield(textBasePackage).click()
          shortcut(Modifier.CONTROL + Key.X, Modifier.META + Key.X)
          typeText(basePackage)
        }
      }
      step("close New Project dialog with Finish") {
        button(buttonFinish).click()
      }
      step("wait when downloading dialog disappears") {
        checkDownloadingDialog()
      }
    }
    waitAMoment()
  }
}

/**
 * Waits for transition animation finished
 * When transition animation occurs components on the appearing page
 * change their [locationOnScreen] coordinates and at the same time their [x] and [y]
 * coordinates are kept unchanged.
 *
 * @param locationOnScreen - function calculated 1 coordinate of locationOnScreen property of a moving component
 * */
fun JDialogFixture.waitForPageTransitionFinished(locationOnScreen: JDialogFixture.() -> Point) {
  step("wait for page transition") {
    var previousCoord = step("calculate original location") { locationOnScreen() }
    robot().waitForIdle()
    GuiTestUtilKt.waitUntil("wait when coordinates stop changing") {
      val currentCoord = step("calculate location in progress") { locationOnScreen() }
//      val currentCoord = GuiTestUtilKt.computeOnEdt { locationOnScreen() }!!
      val result = previousCoord == currentCoord
      logInfo("current coordinates [${currentCoord.x}, ${currentCoord.y}], previous coordinates [${previousCoord.x}, ${previousCoord.y}]")
      previousCoord = currentCoord
      result
    }
    robot().waitForIdle()
    logInfo("page transition finished")
  }
}

fun NewProjectDialogModel.typeGroupAndArtifact(group: String, artifact: String) {
  with(guiTestCase) {
    step("set group and artifact for gradle/maven project") {
      with(connectDialog()) {
        waitForPageTransitionFinished {
          val cmp = textfield(textGroupId).target()
          logInfo("found component ${cmp.hashCode().toString(16)}")
            cmp.locationOnScreen
        }
        step("fill GroupId with `$group`") {
          textfield(textGroupId).click()
          typeText(group)
        }
        step("fill ArtifactId with `$artifact`") {
          textfield(textArtifactId).click()
          typeText(artifact)
        }
      }
    }
  }
}

fun NewProjectDialogModel.createGradleProject(
  projectPath: String,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions,
  projectSdk: String
) {
  with(guiTestCase) {
    step("create gradle-based project") {
      fileSystemUtils.assertProjectPathExists(projectPath)
      with(connectDialog()) {
        selectProjectGroup(NewProjectDialogModel.Groups.Gradle)
        if (projectSdk.isNotEmpty()) selectSdk(projectSdk)
        step("set '$checkKotlinDsl' to '${gradleOptions.useKotlinDsl}'") {
          setCheckboxValue(checkKotlinDsl, gradleOptions.useKotlinDsl)
        }
        if (gradleOptions.framework.isNotEmpty()) {
          step("select framework '${gradleOptions.framework}'") {
            checkboxTree(gradleOptions.framework).check()
            if (gradleOptions.isJavaShouldNotBeChecked)
              checkboxTree(NewProjectDialogModel.Constants.libJava).uncheck()
          }
        }
        else
          logInfo("framework is empty, nothing to select")
        button(buttonNext).click()
        typeGroupAndArtifact(gradleOptions.group, gradleOptions.artifact)
        button(buttonNext).click()
        step("set gradle options") {
          waitForPageTransitionFinished {
            val cmp =checkbox(NewProjectDialogModel.GradleOptions.UseAutoImport.title).target()
            logInfo("found component ${cmp.hashCode().toString(16)}")
            cmp.locationOnScreen
          }
          val useAutoImport = checkbox(NewProjectDialogModel.GradleOptions.UseAutoImport.title)
          if (useAutoImport.isSelected != gradleOptions.useAutoImport) {
            step("change `${NewProjectDialogModel.GradleOptions.UseAutoImport.title}` option") {
              useAutoImport.click()
            }
          }
          val gradleModuleGroup = radioButton(gradleOptions.groupModules.title)
          if (gradleModuleGroup.isSelected().not()) {
            step("select ${gradleModuleGroup.text()}") {
              gradleModuleGroup.click()
              robot().waitForIdle()
              val groupAgain = radioButton(gradleOptions.groupModules.title)
              assert(groupAgain.isSelected()) { "'${groupAgain.text()}' is not selected" }
            }
          }
          val useSeparateModules = checkbox(NewProjectDialogModel.GradleOptions.SeparateModules.title)
          if (useSeparateModules.isSelected != gradleOptions.useSeparateModules) {
            step("change `${NewProjectDialogModel.GradleOptions.SeparateModules.title}` option") {
              useSeparateModules.click()
            }
          }
        }
        button(buttonNext).click()
        typeProjectNameAndLocation(projectPath)
        step("close New Project dialog with Finish") {
          button(buttonFinish).click()
        }
      }
    }
  }
}

fun NewProjectDialogModel.typeProjectNameAndLocation(projectPath: String) {
  with(guiTestCase) {
    with(connectDialog()) {
      waitForPageTransitionFinished {
        val cmp = textfield(textProjectLocation).target()
        logInfo("found component ${cmp.hashCode().toString(16)}")
          cmp.locationOnScreen
      }
      step("fill Project location with `$projectPath`") {
        textfield(textProjectLocation).click()
        shortcut(Modifier.CONTROL + Key.X, Modifier.META + Key.X)
        typeText(projectPath)
      }
      val projectName = projectPath.split(slash).last()
      if (projectName != textfield(textProjectName).text()) {
        step("fill Project name with `$projectName`") {
          textfield(textProjectName).click()
          shortcut(Modifier.CONTROL + Key.X, Modifier.META + Key.X)
          typeText(projectName)
        }
      }
    }
  }
}

fun NewProjectDialogModel.createMavenProject(projectPath: String,
                                             mavenOptions: NewProjectDialogModel.MavenProjectOptions,
                                             projectSdk: String) {
  with(guiTestCase) {
    fileSystemUtils.assertProjectPathExists(projectPath)
    with(connectDialog()) {
      selectProjectGroup(NewProjectDialogModel.Groups.Maven)
      Pause.pause(2000L)
      if (projectSdk.isNotEmpty()) selectSdk(projectSdk)
      if (mavenOptions.useArchetype) {
        step("Set `$checkCreateFromArchetype` checkbox") {
          val archetypeCheckbox = checkbox(checkCreateFromArchetype)
          archetypeCheckbox.isSelected = true
          Pause.pause(1000L)
          if (!archetypeCheckbox.isSelected) {
            step("Checkbox `$checkCreateFromArchetype` not selected, so next attempt") {
              archetypeCheckbox.click()
            }
          }
        }
        step("Select the archetype `${mavenOptions.archetypeVersion}` in the group `$mavenOptions.archetypeGroup`") {
          jTree(mavenOptions.archetypeGroup, mavenOptions.archetypeVersion).clickPath()
        }

      }
      button(buttonNext).click()
      typeGroupAndArtifact(mavenOptions.group, mavenOptions.artifact)

      button(buttonNext).click()

      if (mavenOptions.useArchetype) {
        button(buttonNext).click()
      }
      typeProjectNameAndLocation(projectPath)

      step("close New Project dialog with Finish") {
        button(buttonFinish).click()
      }
    }
  }
}

fun NewProjectDialogModel.createKotlinProject(projectPath: String, framework: String) {
  with(guiTestCase) {
    with(connectDialog()) {
      selectProjectGroup(NewProjectDialogModel.Groups.Kotlin)

      step("select `$framework`") {
        jList(framework).clickItem(framework)
      }
      button(buttonNext).click()

      typeProjectNameAndLocation(projectPath)

      step("close New Project dialog with Finish") {
        button(buttonFinish).click()
      }
    }
  }
}

fun NewProjectDialogModel.createKotlinMPProject(
  projectPath: String,
  templateName: String,
  projectSdk: String
) {
  with(guiTestCase) {
    with(connectDialog()) {
      selectProjectGroup(NewProjectDialogModel.Groups.Kotlin)
      step("select `$templateName` kind of project") {
        jList(templateName).clickItem(templateName)
      }
      button(buttonNext).click()
      val gradleJvm = "Gradle JVM:"
      waitForPageTransitionFinished {
        val cmp = combobox(gradleJvm).target()
        logInfo("found component ${cmp.hashCode().toString(16)}")
          cmp.locationOnScreen
      }
      if (projectSdk.isNotEmpty()) selectSdk(projectSdk, gradleJvm)
      button(buttonNext).click()
      typeProjectNameAndLocation(projectPath)
      step("close New Project dialog with Finish") {
        button(buttonFinish).click()
      }
    }
  }
}

fun NewProjectDialogModel.setCheckboxValue(name: String, value: Boolean) {
  with(guiTestCase) {
    with(connectDialog()) {
      var attempts = 0
      val maxAttempts = 3
      val check = checkbox(name)
      while (check.isSelected != value && attempts <= maxAttempts) {
        step("setCheckboxValue #${attempts + 1}: ${check.target().text} = ${check.isSelected}, expected value = $value") {
          check.click()
          Pause.pause(500L)
          attempts++
        }
      }
    }
  }
}


/**
 * Searches in the New Project dialog specified group in the list of project groups
 * @param group the searched group
 * */
fun NewProjectDialogModel.assertGroupPresent(group: NewProjectDialogModel.Groups) {
  with(guiTestCase) {
    with(connectDialog()) {
      // Group `Java` always exists
      val list: JListFixture = jList(groupJava, timeout = Timeouts.seconds05)
      step("check '${group}' group is present in the New Project dialog") {
        assert(list.contents().contains(group.toString())) {
          "${group} group is absent (may be plugin not installed or Community edition runs instead of Ultimate)"
        }
      }
    }
  }
}

/**
 * Creates a new project from a specified group (ultimate only)
 * Supported only simple groups with 2 pages - first with framework selection and last with specifying project location
 * @param group - group where project is expected to be created. Not all groups are supported
 * @param projectPath - path where the project is going to be created
 * @param libs - set of additional libraries/frameworks that should be checked
 * */
fun NewProjectDialogModel.createProjectInGroup(group: NewProjectDialogModel.Groups,
                                               projectPath: String,
                                               projectSdk: String,
                                               libs: LibrariesSet) {
  with(guiTestCase) {
    fileSystemUtils.assertProjectPathExists(projectPath)
    with(connectDialog()) {
      selectProjectGroup(group)
      if (libs.isSetNotEmpty()) setLibrariesAndFrameworks(libs)
      button(buttonNext).click()
      typeProjectNameAndLocation(projectPath)
      step("close New Project dialog with Finish") {
        button(buttonFinish).click()
      }
      step("wait when downloading dialog disappears") {
        checkDownloadingDialog()
      }
    }
    ideFrame {
      this.waitForBackgroundTasksToFinish()
      waitAMoment()
    }
  }
}

fun NewProjectDialogModel.createGroovyProject(projectPath: String, projectSdk: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.Groovy, projectPath, projectSdk, libs)
}

fun NewProjectDialogModel.createGriffonProject(projectPath: String, projectSdk: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.Griffon, projectPath, projectSdk, libs)
}

fun NewProjectDialogModel.waitLoadingTemplates() {
  GuiTestUtilKt.waitProgressDialogUntilGone(
    GuiRobotHolder.robot,
    progressTitle = progressLoadingTemplates,
    timeoutToAppear = Timeouts.seconds05
  )
}

fun NewProjectDialogModel.setLibrariesAndFrameworks(libs: LibrariesSet) {
  if (libs.isSetEmpty()) return
  with(connectDialog()) {
    for (lib in libs) {
      step("include `${lib.mainPath.joinToString()}` to the project") {
        checkboxTree(
          pathStrings = *lib.mainPath,
          predicate = Predicate.withVersion
        ).check()
      }
    }
  }
}

fun NewProjectDialogModel.selectProjectGroup(group: NewProjectDialogModel.Groups) {
  with(connectDialog()) {
    step("select '$group' project group") {
      val list: JListFixture = jList(groupJava)
      assertGroupPresent(group)
      step("click '$group'") { list.clickItem(group.toString()) }
      list.requireSelection(group.toString())
    }
    waitLoadingTemplates()
  }
}

fun NewProjectDialogModel.selectSdk(sdk: String, sdkField: String = "Project SDK:") {
  with(guiTestCase) {
    step("select '$sdk' as a project SDK") {
      with(connectDialog()) {
        val sdkCombo = combobox(sdkField)
        val selectedItem = sdkCombo.listItems().firstOrNull { it.startsWith(sdk) }
        if (selectedItem != null)
          sdkCombo.selectItem(selectedItem)
        else
          throw IllegalStateException(
            "Required SDK '$sdk' is absent in the \"$sdkField\" list. Found following values: ${sdkCombo.listItems()}")
      }
    }
  }
}

fun NewProjectDialogModel.checkDownloadingDialog() {
  val progressDownloadingDialog = "Downloading"
  GuiTestUtilKt.waitUntil("Wait for downloading finishing", timeout = Timeouts.minutes05) {
    val dialog = try {
      guiTestCase.dialog(
        title = progressDownloadingDialog,
        timeout = Timeouts.seconds03,
        ignoreCaseTitle = true,
        predicate = Predicate.startWith
      )
    }
    catch (e: ComponentLookupException) {
      null
    }
    catch (e: WaitTimedOutError) {
      null
    }
    if (dialog != null) {
      step("found dialog: ${dialog.target().title}") {
        try {
          step("close dialog with button 'Try again'") {
            dialog.button("Try again", timeout = Timeouts.noTimeout).click()
          }
        }
        catch (ignore: ComponentLookupException) {
        }
      }
    }
    dialog == null
  }
}