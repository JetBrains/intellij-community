// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitFileTemplatesFactory
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.util.function.Supplier
import javax.swing.Icon

class IdePluginModuleBuilder : StarterModuleBuilder() {

  private val PLUGIN_TYPE_KEY: Key<PluginType> = Key.create("ide.plugin.type")

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
    return IdePluginInitialStep(contextProvider)
  }

  override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean {
    if (getPluginType() == PluginType.PLUGIN) {
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

    if (getPluginType() == PluginType.PLUGIN) {
      val standardAssetsProvider = StandardAssetsProvider()

      assets.add(GeneratorResourceFile("src/main/resources/META-INF/pluginIcon.svg",
                                       javaClass.getResource("/assets/devkit-pluginIcon.svg")!!))
      assets.add(GeneratorTemplateFile("src/main/resources/META-INF/plugin.xml",
                                       ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.PLUGIN_XML)))
      assets.add(GeneratorTemplateFile("build.gradle.kts", ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.BUILD_GRADLE_KTS)))
      assets.add(GeneratorTemplateFile("settings.gradle.kts", ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.SETTINGS_GRADLE_KTS)))
      assets.add(GeneratorTemplateFile(standardAssetsProvider.gradleWrapperPropertiesLocation,
                                       ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.GRADLE_WRAPPER_PROPERTIES)))

      assets.addAll(standardAssetsProvider.getGradlewAssets())
      assets.addAll(standardAssetsProvider.getGradleIgnoreAssets())

      val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
      if (starterContext.language == JAVA_STARTER_LANGUAGE) {
        assets.add(GeneratorEmptyDirectory("src/main/java/${packagePath}"))
      }
      else if (starterContext.language == KOTLIN_STARTER_LANGUAGE) {
        assets.add(GeneratorEmptyDirectory("src/main/kotlin/${packagePath}"))
      }

      assets.add(GeneratorResourceFile(".run/Run IDE with Plugin.run.xml",
                                       javaClass.getResource("/assets/devkit-Run_IDE_with_Plugin_run.xml")!!))
    }
    else {
      assets.add(GeneratorResourceFile(".gitignore", javaClass.getResource("/assets/devkit-theme.gitignore.txt")!!))
      assets.add(GeneratorResourceFile("resources/META-INF/pluginIcon.svg",
                                       javaClass.getResource("/assets/devkit-pluginIcon.svg")!!))
      assets.add(GeneratorTemplateFile("resources/META-INF/plugin.xml",
                                       ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.THEME_PLUGIN_XML)))
      assets.add(GeneratorTemplateFile("resources/theme/${sanitizeThemeFilename(starterContext.artifact)}.theme.json",
                                       ftManager.getJ2eeTemplate(DevKitFileTemplatesFactory.THEME_JSON)))
    }

    return assets
  }

  override fun getModuleType(): ModuleType<*> {
    if (getPluginType() == PluginType.THEME) {
      return PluginModuleType.getInstance()
    }

    return super.getModuleType()
  }

  override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
    super.setupRootModel(modifiableRootModel)

    if (getPluginType() == PluginType.THEME) {
      val contentEntryPath = contentEntryPath ?: return
      val resourceRootPath = "$contentEntryPath/resources" //NON-NLS
      val contentRoot = LocalFileSystem.getInstance().findFileByPath(contentEntryPath) ?: return

      val contentEntry = MarkRootActionBase.findContentEntry(modifiableRootModel, contentRoot)
      contentEntry?.addSourceFolder(VfsUtilCore.pathToUrl(resourceRootPath), JavaResourceRootType.RESOURCE)
    }
  }

  override fun getGeneratorContextProperties(sdk: Sdk?, dependencyConfig: DependencyConfig): Map<String, String> {
    return mapOf(
      "pluginTitle" to Strings.capitalize(starterContext.artifact),
      "themeName" to sanitizeThemeFilename(starterContext.artifact)
    )
  }

  override fun getFilePathsToOpen(): List<String> {
    if (getPluginType() == PluginType.THEME) {
      return listOf(
        "resources/META-INF/plugin.xml",
        "resources/theme/${sanitizeThemeFilename(starterContext.artifact)}.theme.json"
      )
    }

    return listOf(
      "src/main/resources/META-INF/plugin.xml",
      "build.gradle.kts"
    )
  }

  private fun sanitizeThemeFilename(title: String): String {
    return title.replace("-", "")
      .replace(INVALID_PACKAGE_NAME_SYMBOL_PATTERN, "_")
      .replace(Regex("\\s"), "")
  }

  fun setPluginType(pluginType: PluginType) {
    starterContext.putUserData(PLUGIN_TYPE_KEY, pluginType)
  }

  private fun getPluginType(): PluginType {
    return starterContext.getUserData(PLUGIN_TYPE_KEY) ?: PluginType.PLUGIN
  }

  private inner class IdePluginInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {
    private val typeProperty: GraphProperty<PluginType> = propertyGraph.property(PluginType.PLUGIN)

    override fun addFieldsBefore(layout: Panel) {
      layout.row(DevKitBundle.message("module.builder.type")) {
        segmentedButton(listOf(PluginType.PLUGIN, PluginType.THEME)) { it.messagePointer.get() }
          .bind(typeProperty)
      }.bottomGap(BottomGap.SMALL)

      setPluginType(PluginType.PLUGIN)

      typeProperty.afterChange { pluginType ->
        setPluginType(pluginType)

        languageRow.visible(pluginType == PluginType.PLUGIN)
        groupRow.visible(pluginType == PluginType.PLUGIN)
        artifactRow.visible(pluginType == PluginType.PLUGIN)

        // Theme / Plugin projects require different SDK type
        sdkComboBox.selectedJdk = null
        sdkComboBox.reloadModel()
        ProjectWizardUtil.preselectJdkForNewModule(wizardContext.project, null, sdkComboBox, Condition(::isSuitableSdkType))
      }
    }

    override fun addFieldsAfter(layout: Panel) {
      layout.row {
        hyperLink(DevKitBundle.message("module.builder.how.to.link"),
                  "https://plugins.jetbrains.com/docs/intellij/intellij-platform.html")
      }
      layout.row {
        hyperLink(DevKitBundle.message("module.builder.github.template.link"),
                  "https://jb.gg/plugin-template")
      }

      val scalaPluginId = PluginId.findId("org.intellij.scala")
      if (scalaPluginId != null && PluginManager.isPluginInstalled(scalaPluginId)) {
        layout.row {
          hyperLink(DevKitBundle.message("module.builder.scala.github.template.link"),
                    "https://github.com/JetBrains/sbt-idea-plugin")
        }
      }
    }
  }

  enum class PluginType(
    val messagePointer: Supplier<String>
  ) {
    PLUGIN(DevKitBundle.messagePointer("module.builder.type.plugin")),
    THEME(DevKitBundle.messagePointer("module.builder.type.theme"))
  }
}