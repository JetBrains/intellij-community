// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.gradle.GradleResourcesProvider
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.layout.*
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitFileTemplatesFactory
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import java.util.function.Supplier
import javax.swing.Icon

class DevKitModuleBuilder : StarterModuleBuilder() {

  private val PLUGIN_TYPE_KEY: Key<PluginType> = Key.create("devkit.plugin.type")

  override fun getBuilderId(): String = "idea-plugin"
  override fun getPresentableName(): String = DevKitBundle.message("module.builder.title")
  override fun getWeight(): Int = IJ_PLUGIN_WEIGHT
  override fun getNodeIcon(): Icon = AllIcons.Nodes.Plugin
  override fun getDescription(): String = DevKitBundle.message("module.description")

  override fun getProjectTypes(): List<StarterProjectType> = emptyList()
  override fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
  override fun getMinJavaVersion(): JavaVersion = LanguageLevel.JDK_11.toJavaVersion()

  override fun getLanguages(): List<StarterLanguage> {
    return listOf(
      JAVA_STARTER_LANGUAGE,
      KOTLIN_STARTER_LANGUAGE
    )
  }

  override fun getStarterPack(): StarterPack {
    return StarterPack("devkit", listOf(
      Starter("devkit", "DevKit", getDependencyConfig("/starters/devkit.pom"), emptyList())
    ))
  }

  override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
    return emptyArray()
  }

  override fun createOptionsStep(contextProvider: StarterContextProvider): StarterInitialStep {
    return DevKitInitialStep(contextProvider)
  }

  override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean {
    val pluginType = starterContext.getUserData(PLUGIN_TYPE_KEY) ?: PluginType.PLUGIN

    if (pluginType == PluginType.PLUGIN) {
      return super.isSuitableSdkType(sdkType)
    }

    return sdkType == IdeaJdk.getInstance()
  }

  override fun setupModule(module: Module) {
    // manually set, we do not show the second page with libraries
    starterContext.starter = starterContext.starterPack.starters.first()
    starterContext.starterDependencyConfig = loadDependencyConfig()[starterContext.starter?.id]

    super.setupModule(module)
  }

  override fun getAssets(starter: Starter): List<GeneratorAsset> {
    val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)

    val assets = mutableListOf<GeneratorAsset>()
    assets.add(GeneratorTemplateFile("build.gradle.kts", ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.BUILD_GRADLE_KTS)))
    assets.add(GeneratorTemplateFile("settings.gradle.kts", ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.SETTINGS_GRADLE_KTS)))
    assets.add(GeneratorTemplateFile("gradle/wrapper/gradle-wrapper.properties",
                                     ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.GRADLE_WRAPPER_PROPERTIES)))
    assets.addAll(GradleResourcesProvider().getGradlewResources())

    val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
    if (starterContext.language == JAVA_STARTER_LANGUAGE) {
      assets.add(GeneratorEmptyDirectory("src/main/java/${packagePath}"))
    }
    else if (starterContext.language == KOTLIN_STARTER_LANGUAGE) {
      assets.add(GeneratorEmptyDirectory("src/main/kotlin/${packagePath}"))
    }

    assets.add(GeneratorTemplateFile("src/main/resources/META-INF/plugin.xml",
                                     ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.PLUGIN_XML)))
    assets.add(GeneratorResourceFile("src/main/resources/META-INF/pluginIcon.svg",
                                     javaClass.getResource("/assets/devkit-pluginIcon.svg")!!))

    assets.add(GeneratorResourceFile(".run/Run IDE with Plugin.run.xml",
                                     javaClass.getResource("/assets/devkit-Run_IDE_with_Plugin_run.xml")!!))

    return assets
  }

  override fun getGeneratorContextProperties(sdk: Sdk?, dependencyConfig: DependencyConfig): Map<String, String> {
    return mapOf("pluginTitle" to Strings.capitalize(starterContext.artifact))
  }

  override fun getFilePathsToOpen(): List<String> {
    return listOf(
      "src/main/resources/META-INF/plugin.xml",
      "build.gradle.kts"
    )
  }

  private inner class DevKitInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {
    private val typeProperty: GraphProperty<PluginType> = propertyGraph.graphProperty { PluginType.PLUGIN }

    override fun addFieldsBefore(layout: LayoutBuilder) {
      layout.row(DevKitBundle.message("module.builder.type")) {
        segmentedButton(listOf(PluginType.PLUGIN, PluginType.THEME), typeProperty) { it.messagePointer.get() }
      }.largeGapAfter()

      starterContext.putUserData(PLUGIN_TYPE_KEY, PluginType.PLUGIN)

      typeProperty.afterChange { pluginType ->
        starterContext.putUserData(PLUGIN_TYPE_KEY, pluginType)

        languageRow.visible = pluginType == PluginType.PLUGIN
        groupRow.visible = pluginType == PluginType.PLUGIN
        artifactRow.visible = pluginType == PluginType.PLUGIN

        // Theme / Plugin projects require different SDK type
        sdkComboBox.selectedJdk = null
        sdkComboBox.reloadModel()
        ProjectWizardUtil.preselectJdkForNewModule(wizardContext.project, null, sdkComboBox, Condition(::isSuitableSdkType))
      }
    }

    override fun addFieldsAfter(layout: LayoutBuilder) {
      layout.row {
        hyperLink(DevKitBundle.message("module.builder.how.to.link"),
                  "https://plugins.jetbrains.com/docs/intellij/intellij-platform.html")
      }
      layout.row {
        hyperLink(DevKitBundle.message("module.builder.github.template.link"),
                  "https://github.com/JetBrains/intellij-platform-plugin-template")
      }

      if (PluginManager.isPluginInstalled(PluginId.findId("org.intellij.scala"))) {
        layout.row {
          hyperLink(DevKitBundle.message("module.builder.scala.github.template.link"),
                    "https://github.com/JetBrains/sbt-idea-plugin")
        }
      }
    }
  }

  private fun Row.hyperLink(@Nls title: String, @NlsSafe url: String) {
    val hyperlinkLabel = HyperlinkLabel(title)
    hyperlinkLabel.setHyperlinkTarget(url)
    hyperlinkLabel.toolTipText = url
    this.component(hyperlinkLabel)
  }

  private enum class PluginType(
    val messagePointer: Supplier<String>
  ) {
    PLUGIN(DevKitBundle.messagePointer("module.builder.type.plugin")),
    THEME(DevKitBundle.messagePointer("module.builder.type.theme"))
  }
}