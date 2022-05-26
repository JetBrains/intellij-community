// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.gradle.GradleResourcesProvider
import com.intellij.ide.starters.shared.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.javaFX.JavaFXBundle
import javax.swing.Icon

internal class JavaFxModuleBuilder : StarterModuleBuilder() {
  override fun getBuilderId(): String = "javafx"
  override fun getNodeIcon(): Icon = AllIcons.Nodes.Module
  override fun getPresentableName(): String = JavaFXBundle.JAVA_FX
  override fun getDescription(): String = JavaFXBundle.message("javafx.module.builder.description")
  override fun getWeight(): Int = super.getWeight() + 1

  override fun getMinJavaVersion(): JavaVersion = LanguageLevel.JDK_11.toJavaVersion()

  override fun getProjectTypes(): List<StarterProjectType> {
    return listOf(MAVEN_PROJECT, GRADLE_PROJECT)
  }

  override fun getLanguages(): List<StarterLanguage> {
    return listOf(
      JAVA_STARTER_LANGUAGE,
      KOTLIN_STARTER_LANGUAGE,
      GROOVY_STARTER_LANGUAGE
    )
  }

  override fun getTestFrameworks(): List<StarterTestRunner> {
    return listOf(JUNIT_TEST_RUNNER, TESTNG_TEST_RUNNER)
  }

  override fun getStarterPack(): StarterPack {
    return StarterPack("javafx", listOf(
      Starter("javafx", JavaFXBundle.JAVA_FX, getDependencyConfig("/starters/javafx.pom"), listOf(
        Library("bootstrapfx", null, "BootstrapFX", JavaFXBundle.message("library.bootstrapfx.description"),
                "org.kordamp.bootstrapfx", "bootstrapfx-core", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/kordamp/bootstrapfx")
        )),
        Library("controlsfx", null, "ControlsFX", JavaFXBundle.message("library.controlsfx.description"),
                "org.controlsfx", "controlsfx", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://controlsfx.github.io/")
        )),
        Library("formsfx", null, "FormsFX", JavaFXBundle.message("library.formsfx.description"),
                "com.dlsc.formsfx", "formsfx-core", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/dlsc-software-consulting-gmbh/FormsFX/")
        )),
        Library("fxgl", null, "FXGL", JavaFXBundle.message("library.fxgl.description"),
                "com.github.almasb", "fxgl", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/AlmasB/FXGL")
        )),
        Library("ikonli", null, "Ikonli", JavaFXBundle.message("library.ikonli.description"),
                "org.kordamp.ikonli", "ikonli-javafx", listOf(
          LibraryLink(LibraryLinkType.REFERENCE, "https://kordamp.org/ikonli/")
        )),
        Library("tilesfx", null, "TilesFX", JavaFXBundle.message("library.tilesfx.description"),
                "eu.hansolo", "tilesfx", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/HanSolo/tilesfx")
        )),
        Library("validatorfx", null, "ValidatorFX", JavaFXBundle.message("library.validatorfx.description"),
                "net.synedra", "validatorfx", listOf(
          LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/effad/ValidatorFX")
        ))
      ))
    ))
  }

  override fun getFilePathsToOpen(): List<String> {
    val files = mutableListOf<String>()
    if (starterContext.projectType == MAVEN_PROJECT) {
      files.add("pom.xml")
    }
    else if (starterContext.projectType == GRADLE_PROJECT) {
      files.add("build.gradle")
    }

    val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
    val samplesLanguage = starterContext.language.id
    val samplesExt = getSamplesExt(starterContext.language)

    files.add("src/main/resources/${packagePath}/hello-view.fxml")
    files.add("src/main/${samplesLanguage}/${packagePath}/HelloController.${samplesExt}")
    files.add("src/main/${samplesLanguage}/${packagePath}/HelloApplication.${samplesExt}")

    return files
  }

  override fun getGeneratorContextProperties(sdk: Sdk?, dependencyConfig: DependencyConfig): Map<String, String> {
    val properties = HashMap(super.getGeneratorContextProperties(sdk, dependencyConfig))

    val sdkVersion = sdk?.let { JavaSdk.getInstance().getVersion(it) }
    val sdkFeatureVersion = sdkVersion?.maxLanguageLevel?.toJavaVersion()?.feature

    if (sdkFeatureVersion == null) {
      properties["javafx.version"] = getUnknownFxVersion(dependencyConfig)
    }
    else {
      val targetVersion = dependencyConfig.properties["fx${sdkFeatureVersion}.version"]
      properties["javafx.version"] = targetVersion ?: getUnknownFxVersion(dependencyConfig)
    }

    return properties
  }

  private fun getUnknownFxVersion(dependencyConfig: DependencyConfig): String? {
    return dependencyConfig.properties["fx.default.version"]
  }

  override fun getAssets(starter: Starter): List<GeneratorAsset> {
    val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)

    val assets = mutableListOf<GeneratorAsset>()
    if (starterContext.projectType == GRADLE_PROJECT) {
      assets.add(GeneratorTemplateFile("build.gradle", ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_BUILD_GRADLE)))
      assets.add(GeneratorTemplateFile("settings.gradle", ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_SETTINGS_GRADLE)))
      assets.add(GeneratorTemplateFile("gradle/wrapper/gradle-wrapper.properties",
                                       ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_GRADLEW_PROPERTIES)))
      assets.addAll(GradleResourcesProvider().getGradlewResources())
    }
    else if (starterContext.projectType == MAVEN_PROJECT) {
      assets.add(GeneratorTemplateFile("pom.xml", ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_POM_XML)))
    }

    val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
    val samplesLanguage = starterContext.language.id
    val samplesExt = getSamplesExt(starterContext.language)

    if (starterContext.projectType == MAVEN_PROJECT) {
      // kotlin in Maven uses single src/main/kotlin source root
      assets.add(GeneratorTemplateFile("src/main/${samplesLanguage}/module-info.java",
                                       ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_MODULE_INFO_JAVA)))
    }
    else {
      assets.add(GeneratorTemplateFile("src/main/java/module-info.java",
                                       ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_MODULE_INFO_JAVA)))
    }

    assets.add(GeneratorTemplateFile("src/main/${samplesLanguage}/${packagePath}/HelloApplication.${samplesExt}",
                                     ftManager.getJ2eeTemplate("javafx-HelloApplication-${samplesLanguage}.${samplesExt}")))
    assets.add(GeneratorTemplateFile("src/main/${samplesLanguage}/${packagePath}/HelloController.${samplesExt}",
                                     ftManager.getJ2eeTemplate("javafx-HelloController-${samplesLanguage}.${samplesExt}")))
    assets.add(GeneratorTemplateFile("src/main/resources/${packagePath}/hello-view.fxml",
                                     ftManager.getJ2eeTemplate(JavaFxModuleTemplateGroup.JAVAFX_HELLO_VIEW_FXML)))

    return assets
  }
}