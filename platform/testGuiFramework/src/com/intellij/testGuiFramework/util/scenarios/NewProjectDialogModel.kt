// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.framework.GuiTestUtil.typeText
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonCancel
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonFinish
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonNew
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonNext
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.buttonOk
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateFromArchetype
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateJsModule
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateJvmModule
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkCreateProjectFromTemplate
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.checkKotlinDsl
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.comboHierarchyKind
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
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupSpringInitializer
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.groupStaticWeb
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.itemKotlinMpp
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.progressLoadingTemplates
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.progressSearchingForAppServerLibraries
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textApplicationServer
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textArtifactId
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textBasePackage
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textGroupId
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textProjectLocation
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textProjectName
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.Constants.textRootModuleName
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Pause

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
    const val comboHierarchyKind = "Hierarchy kind:"
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
    const val groupSpringInitializer = "Spring Initializer"
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
    const val libJBoss = "JBoss"
    const val libArquillianJUnit = "Arquillian JUnit"
    const val libArquillianTestNG = "Arquillian TestNG"
    const val libJBossDrools = "JBoss Drools"
    const val itemKotlinMpp = "Kotlin (Multiplatform - Experimental)"
  }

  enum class Groups(private val title: String) {
    Java(groupJava), JavaEnterprise(groupJavaEnterprise), JBoss(groupJBoss),
    J2ME(groupJ2ME), Clouds(groupClouds), Spring(groupSpring), JavaFX(groupJavaFX),
    Android(groupAndroid), IPPlugin(groupIntelliJPlatformPlugin), SpringInitializer(groupSpringInitializer),
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
    val groupModules: GradleGroupModules = GradleGroupModules.ExplicitModuleGroups)

  data class MavenProjectOptions(
    val group: String = "mavenGroup",
    val artifact: String,
    val useArchetype: Boolean = false,
    val archetypeGroup: String = "",
    val archetypeVersion: String = ""
  )

  enum class MppProjectStructure(private val title: String) {
    RootEmptyModule("Root empty module with common & platform children"),
    RootCommonModule("Root common module with children platform modules")
    ;

    override fun toString() = title
  }
}

val GuiTestCase.newProjectDialogModel by NewProjectDialogModel

fun NewProjectDialogModel.connectDialog(): JDialogFixture =
  testCase.dialog(NewProjectDialogModel.Constants.newProjectTitle, true, defaultTimeout)

fun assertProjectPathExists(projectPath: String) {
  assert(FileUtil.exists(projectPath)) { "Test project $projectPath should be created before test starting" }
}

typealias LibrariesSet = Set<Array<String>>

/**
 * Creates a new project from Java group
 * @param projectPath - path where the project is going to be created
 * @param libs - path to additional library/framework that should be checked
 * Note: only one library/framework can be checked!
 * */
fun NewProjectDialogModel.createJavaProject(projectPath: String, libs: LibrariesSet = emptySet(), template: String = "", basePackage: String = "") {
  assertProjectPathExists(projectPath)
  val setLibraries = libs.isNotEmpty()
  val setTemplate = template.isNotEmpty()
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupJava)
      list.clickItem(groupJava)
      if (setLibraries) {
        for (lib in libs) {
          logUIStep("Include `${lib.joinToString()}` to the project")
          checkboxTree(*lib).check(*lib)
        }
      }
      else {
        button(buttonNext).click()
        if(setTemplate){
          checkbox(checkCreateProjectFromTemplate).isSelected = true
          jList(template).clickItem(template)
        }
      }
      button(buttonNext).click()
      logUIStep("Fill Project location with `$projectPath`")
      textfield(textProjectName).click()
      shortcut(Key.TAB)
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)
      if(setTemplate && basePackage.isNotEmpty()){
        // base package is set only for Command Line app template
        logUIStep("Set Base package to `$basePackage`")
        textfield(textBasePackage).click()
        shortcut(Modifier.CONTROL + Key.X)
        typeText(basePackage)
      }
      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
    ideFrame {
      this.waitForBackgroundTasksToFinish()
      waitAMoment()
    }
  }
}

/**
 * Creates a new project from Java Enterprise group (ultimate only)
 * @param projectPath - path where the project is going to be created
 * @param libs - path to additional library/framework that should be checked
 * Note: only one library/framework can be checked!
 * */
fun NewProjectDialogModel.createJavaEnterpriseProject(projectPath: String, libs: LibrariesSet = emptySet(), template: String = "") {
  assertProjectPathExists(projectPath)
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupJava)
      assertGroupPresent(NewProjectDialogModel.Groups.JavaEnterprise)
      list.clickItem(groupJavaEnterprise)
      if (libs.isEmpty()) {
        button(buttonNext).click()
        if(template.isNotEmpty()){
          checkbox(checkCreateProjectFromTemplate).isSelected = true
          jList(template).clickItem(template)
        }
      }
      else {
        for (lib in libs) {
          logUIStep("Include `${lib.joinToString()}` to the project")
          checkboxTree(*lib).check(*lib)
        }
      }
      button(buttonNext).click()
      logUIStep("Fill Project location with `$projectPath`")
      textfield(textProjectLocation).click()
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)
      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
    ideFrame {
      this.waitForBackgroundTasksToFinish()
      waitAMoment()
    }
  }
}

fun NewProjectDialogModel.createGradleProject(projectPath: String, gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
  assertProjectPathExists(projectPath)
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupGradle)
      list.clickItem(groupGradle)
      setCheckboxValue(checkKotlinDsl, gradleOptions.useKotlinDsl)
      if (gradleOptions.framework.isNotEmpty()) {
        checkboxTree(gradleOptions.framework).check(gradleOptions.framework)
        if (gradleOptions.isJavaShouldNotBeChecked)
          checkboxTree(gradleOptions.framework).uncheck("Java")
      }
      button(buttonNext).click()
      logUIStep("Fill GroupId with `${gradleOptions.group}`")
      textfield(textGroupId).click()
      typeText(gradleOptions.group)
      logUIStep("Fill ArtifactId with `${gradleOptions.artifact}`")
      textfield(textArtifactId).click()
      typeText(gradleOptions.artifact)
      button(buttonNext).click()
      println(gradleOptions)
      val useAutoImport = checkbox(NewProjectDialogModel.GradleOptions.UseAutoImport.title)
      if (useAutoImport.isSelected != gradleOptions.useAutoImport) {
        logUIStep("Change `${NewProjectDialogModel.GradleOptions.UseAutoImport.title}` option")
        useAutoImport.click()
      }
      //      val explicitGroup = radioButton(GradleGroupModules.ExplicitModuleGroups.title)
      //      logUIStep("explicit group found")
      //      val qualifiedNames = radioButton(GradleGroupModules.QualifiedNames.title)
      //      logUIStep("qualified names found")
      //      when(gradleOptions.groupModules){
      //        GradleGroupModules.ExplicitModuleGroups -> {
      //          logUIStep("Choose '${GradleGroupModules.ExplicitModuleGroups.title}' option")
      //          explicitGroup.click()
      //        }
      //        GradleGroupModules.QualifiedNames -> {
      //          logUIStep("Choose '${GradleGroupModules.QualifiedNames.title}' option")
      //          qualifiedNames.click()
      //        }
      //      }
      val useSeparateModules = checkbox(NewProjectDialogModel.GradleOptions.SeparateModules.title)
      if (useSeparateModules.isSelected != gradleOptions.useSeparateModules) {
        logUIStep("Change `${NewProjectDialogModel.GradleOptions.SeparateModules.title}` option")
        useSeparateModules.click()
      }
      button(buttonNext).click()
      logUIStep("Fill Project location with `$projectPath`")
      // Field "Project location" is located under additional panel and has location [0,0], that's why we usually click into field "Project name"
      textfield(textProjectName).click()
      shortcut(Key.TAB)
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)
      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
  }
}

fun NewProjectDialogModel.createMavenProject(projectPath: String, mavenOptions: NewProjectDialogModel.MavenProjectOptions) {
  assertProjectPathExists(projectPath)
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupMaven)
      list.clickItem(groupMaven)
      Pause.pause(2000L)
      if (mavenOptions.useArchetype) {
        logUIStep("Set `$checkCreateFromArchetype` checkbox")
        val archetypeCheckbox = checkbox(checkCreateFromArchetype)
        archetypeCheckbox.isSelected = true
        Pause.pause(1000L)
        if (!archetypeCheckbox.isSelected) {
          logUIStep("Checkbox `$checkCreateFromArchetype` not selected, so next attempt")
          archetypeCheckbox.click()
        }

        logUIStep("Double click on `${mavenOptions.archetypeGroup}` in the archetype list")
        jTree(mavenOptions.archetypeGroup).doubleClickPath(mavenOptions.archetypeGroup)
        logUIStep("Select the archetype `${mavenOptions.archetypeVersion}` in the group `$mavenOptions.archetypeGroup`")
        jTree(mavenOptions.archetypeGroup, mavenOptions.archetypeVersion).clickPath(mavenOptions.archetypeGroup,
                                                                                    mavenOptions.archetypeVersion)

      }
      button(buttonNext).click()

      logUIStep("Fill $textGroupId with `${mavenOptions.group}`")
      typeText(mavenOptions.group)
      shortcut(Key.TAB)
      logUIStep("Fill $textArtifactId with `${mavenOptions.artifact}`")
      typeText(mavenOptions.artifact)

      button(buttonNext).click()

      if (mavenOptions.useArchetype) {
        button(buttonNext).click()
      }

      logUIStep("Fill `$textProjectLocation` with `$projectPath`")
      textfield(textProjectLocation).click()
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)

      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
  }
}

fun NewProjectDialogModel.createKotlinProject(projectPath: String, framework: String) {
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupKotlin)
      list.clickItem(groupKotlin)

      logUIStep("Select `$framework`")
      jList(framework).clickItem(framework)
      button(buttonNext).click()

      logUIStep("Fill $textProjectLocation with `$projectPath`")
      textfield(textProjectLocation).click()
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)

      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
  }
}

fun NewProjectDialogModel.createKotlinMPProject(
  projectPath: String,
  moduleName: String,
  mppProjectStructure: NewProjectDialogModel.MppProjectStructure,
  isJvmIncluded: Boolean = true,
  isJsIncluded: Boolean = true
) {
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupKotlin)
      list.clickItem(groupKotlin)
      logUIStep("Select `$itemKotlinMpp` kind of project")
      jList(itemKotlinMpp).clickItem(itemKotlinMpp)
      button(buttonNext).click()
      val cmb = combobox(comboHierarchyKind)
      logUIStep("Select MP project hierarchy kind: `$mppProjectStructure`")
      if (cmb.selectedItem() != mppProjectStructure.toString()) {
        cmb
          .expand()
          .selectItem(mppProjectStructure.toString())
        logInfo("Combobox `$comboHierarchyKind`: current selected item is `${cmb.selectedItem()}` ")
      }

      logUIStep("Type root module name `$moduleName`")
      textfield(textRootModuleName).click()
      shortcut(Modifier.CONTROL + Key.A)
      typeText(moduleName)
      if (!isJvmIncluded) {
        logUIStep("No need JVM module, uncheck `$checkCreateJvmModule`")
        checkbox(checkCreateJvmModule).click()
      }
      if (!isJsIncluded) {
        logUIStep("No need JS module, uncheck `$checkCreateJsModule`")
        checkbox(checkCreateJsModule).click()
      }
      button(buttonNext).click()
      button(buttonNext).click()
      logUIStep("Type $textProjectLocation `$projectPath`")
      textfield(textProjectLocation).click()
      shortcut(Modifier.CONTROL + Key.A)
      typeText(projectPath)
      button(buttonFinish).click()
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
        logUIStep("setCheckboxValue #${attempts + 1}: ${check.target().name} = ${check.isSelected}, expected value = $value")
        check.click()
        Pause.pause(500L)
        attempts++
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
      val list: JListFixture = jList("Java")
      logTestStep("Check ${group} is present in the New Project dialog")
      assert(list.contents().contains(group.toString())) {
        "${group} group is absent (may be plugin not installed or Community edition runs instead of Ultimate)"
      }
    }
  }
}

/**
 * Creates a new project from a specified group (ultimate only)
 * Supported only simple groups with 2 pages - first with framework selection and last with specifying project location
 * @param group - group where project is expected to be created. Not all groups are supported
 * @param projectPath - path where the project is going to be created
 * @param libs - path to additional library/framework that should be checked
 * Note: only one library/framework can be checked!
 * */
internal fun NewProjectDialogModel.createProjectInGroup(group: NewProjectDialogModel.Groups,
                                                        projectPath: String,
                                                        libs: LibrariesSet) {
  assertProjectPathExists(projectPath)
  with(guiTestCase) {
    with(connectDialog()) {
      val list: JListFixture = jList(groupJava)
      assertGroupPresent(group)
      list.clickItem(group.toString())
      for (lib in libs) {
        logUIStep("Include `${lib.joinToString()}` to the project")
        checkboxTree(*lib).check(*lib)
      }
      button(buttonNext).click()
      logUIStep("Fill Project location with `$projectPath`")
      textfield(textProjectLocation).click()
      shortcut(Modifier.CONTROL + Key.X)
      typeText(projectPath)
      logUIStep("Close New Project dialog with Finish")
      button(buttonFinish).click()
    }
    ideFrame {
      this.waitForBackgroundTasksToFinish()
      waitAMoment()
    }
  }
}

fun NewProjectDialogModel.createJBossProject(projectPath: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.JBoss, projectPath, libs)
}

fun NewProjectDialogModel.createSpringProject(projectPath: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.Spring, projectPath, libs)
}

fun NewProjectDialogModel.createGroovyProject(projectPath: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.Groovy, projectPath, libs)
}

fun NewProjectDialogModel.createGriffonProject(projectPath: String, libs: LibrariesSet) {
  createProjectInGroup(NewProjectDialogModel.Groups.Griffon, projectPath, libs)
}

fun NewProjectDialogModel.waitLoadingTemplates(){
  GuiTestUtilKt.waitProgressDialogUntilGone(
    GuiRobotHolder.robot,
    progressTitle = progressLoadingTemplates
  )
}

fun NewProjectDialogModel.createAppServer(serverKind: String, serverInstallPath: String) {
  with(connectDialog()) {
    val list: JListFixture = jList(groupJavaEnterprise)
    assertGroupPresent(NewProjectDialogModel.Groups.JavaEnterprise)
    list.clickItem(groupJavaEnterprise)
    guiTestCase.logUIStep("Add a new application server")
    combobox(textApplicationServer)
    buttons(buttonNew)[1].click()
    popupClick(serverKind)
    guiTestCase.dialog(serverKind) {
      typeText(serverInstallPath)
      button(buttonOk).click()
      GuiTestUtilKt.waitProgressDialogUntilGone(
        GuiRobotHolder.robot,
        progressTitle = progressSearchingForAppServerLibraries
      )

    }
    button(buttonCancel).click()
  }
}

fun NewProjectDialogModel.checkAppServerExists(serverName: String) {
  with(connectDialog()) {
    val list: JListFixture = jList(groupJavaEnterprise)
    assertGroupPresent(NewProjectDialogModel.Groups.JavaEnterprise)
    list.clickItem(groupJavaEnterprise)
    guiTestCase.logUIStep("Check that a application server `$serverName` exists")
    val cmb = combobox(textApplicationServer)
    assert(combobox(textApplicationServer)
             .listItems()
             .contains(serverName)) { "Appserver `$serverName` doesn't exist" }
    button(buttonCancel).click()
  }
}