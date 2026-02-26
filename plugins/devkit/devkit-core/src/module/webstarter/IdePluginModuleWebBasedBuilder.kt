// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module.webstarter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.INVALID_PACKAGE_NAME_SYMBOL_PATTERN
import com.intellij.ide.starters.remote.DownloadResult
import com.intellij.ide.starters.remote.WebStarterContext
import com.intellij.ide.starters.remote.WebStarterContextProvider
import com.intellij.ide.starters.remote.WebStarterDependency
import com.intellij.ide.starters.remote.WebStarterDependencyCategory
import com.intellij.ide.starters.remote.WebStarterModuleBuilder
import com.intellij.ide.starters.remote.WebStarterServerOptions
import com.intellij.ide.starters.remote.wizard.WebStarterInitialStep
import com.intellij.ide.starters.remote.wizard.WebStarterLibrariesStep
import com.intellij.ide.starters.shared.CustomizedMessages
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.LibraryLinkType
import com.intellij.ide.starters.shared.StarterLanguage
import com.intellij.ide.starters.shared.StarterProjectType
import com.intellij.ide.starters.shared.hyperLink
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.ZipUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.module.DEVKIT_NEWLY_GENERATED_PROJECT
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.io.File
import java.util.function.Supplier
import javax.swing.Icon

internal open class IdePluginModuleWebBasedBuilder : WebStarterModuleBuilder() {

  private val PLUGIN_TYPE_KEY: Key<PluginType> = Key.create("ide.plugin.type")
  private val PLUGIN_TYPE_PACK_GROUP_ID = "org.jetbrains.intellij.platform"
  private val PACK_GROUPS_ORDER = listOf(
    "org.jetbrains.intellij.platform.dependencies",
    "org.jetbrains.intellij.platform.plugins",
    "org.jetbrains.intellij.platform.misc",
    "org.jetbrains.intellij.platform.samples",
  )

  override fun getBuilderId(): String = "ide-plugin-web-starter"
  override fun getWeight(): Int = JVM_WEIGHT + 1000
  override fun getDefaultServerUrl(): String = "https://plugins.jetbrains.com/generator"
  override fun getNodeIcon(): Icon = AllIcons.Nodes.Plugin
  override fun getPresentableName(): String = message("module.builder.title")
  override fun getDescription(): String = message("module.description")
  override fun getLanguages(): List<StarterLanguage> = listOf(KOTLIN_STARTER_LANGUAGE) // Java and Kotlin both are available out of the box
  override fun getProjectTypes(): List<StarterProjectType> = emptyList()

  override fun loadServerOptions(serverUrl: String): WebStarterServerOptions {
    val packsJson = loadJsonData("$serverUrl/api/packs") as ArrayNode
    return parsePacks(packsJson)
  }

  private fun parsePacks(packs: ArrayNode): WebStarterServerOptions {
    val categories = mutableMapOf<String, PackCategory>()
    for (pack in packs) {
      if (pack !is ObjectNode) continue
      val properties = pack["properties"] as? ArrayNode
      if (isHiddenInWizard(properties)) continue
      val category = parseAndGetOrCreateCategory(pack, categories) ?: continue
      category.extensions.add(createDependency(pack))
    }
    categories.values.forEach { it.extensions.sortBy(WebStarterDependency::title) }
    return WebStarterServerOptions(emptyList(), sortCategoriesByOrder(categories))
  }

  override fun isExampleCodeProvided(): Boolean = true

  private fun isHiddenInWizard(properties: ArrayNode?): Boolean {
    return properties?.findPropertyDefaultValue("hiddenInWizard")?.asBoolean() ?: false
  }

  private fun ArrayNode.findPropertyDefaultValue(propertyName: String): JsonNode? {
    return find { it["key"].asText() == propertyName }?.get("default")
  }

  private fun parseAndGetOrCreateCategory(info: ObjectNode, categories: MutableMap<String, PackCategory>): PackCategory? {
    val groupId = info["group"]?.get("id")?.asText().takeIf { it != PLUGIN_TYPE_PACK_GROUP_ID } ?: return null
    val groupTitle = message("module.builder.web.category.$groupId.label") ?: groupId
    return categories.getOrPut(groupId) { PackCategory(groupTitle, groupId) }
  }

  private fun createDependency(info: ObjectNode): WebStarterDependency {
    val id = info["id"].asText()
    val name = info["name"].asText()
    val description = info["description"]?.asText()
    val links = (info["links"] as? ObjectNode)?.let { linksObj ->
      buildList {
        addLinkIfPresent(linksObj, "home", LibraryLinkType.WEBSITE)
        addLinkIfPresent(linksObj, "docs", LibraryLinkType.REFERENCE)
        addLinkIfPresent(linksObj, "guide", LibraryLinkType.GUIDE)
        addLinkIfPresent(linksObj, "specification", LibraryLinkType.SPECIFICATION)
      }
    } ?: emptyList()
    val icon = info["icon"]?.asText()?.let { IconLoader.findIcon(it, this.javaClass.classLoader) }
    val dependency = WebStarterDependency(
      id = id,
      title = name,
      description = description,
      links = links,
      isDefault = false,
      icon = icon
    )
    return dependency
  }

  private fun MutableList<LibraryLink>.addLinkIfPresent(linksObj: ObjectNode, key: String, type: LibraryLinkType) {
    linksObj[key]?.asText()?.let { add(LibraryLink(type, it)) }
  }

  private fun sortCategoriesByOrder(categories: MutableMap<String, PackCategory>): List<PackCategory> =
    categories.values.sortedBy { category ->
      PACK_GROUPS_ORDER.indexOf(category.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

  @RequiresBackgroundThread
  override fun downloadResult(progressIndicator: ProgressIndicator, tempFile: File): DownloadResult {
    val payload = objectMapper.createObjectNode().apply {
      put("group", starterContext.group) // ignored for themes on the generator side but required by KASTLE
      put(
        "name",
        when (getPluginType()) {
          PluginType.PLUGIN -> starterContext.artifact
          PluginType.THEME -> starterContext.name // send name because theme plugins don't specify group and artifact
        }
      )
      putArray("packs").apply { addPacks() }
      putObject("properties").apply { putProperties() }
    }

    val url = composeGeneratorUrl(starterContext.serverUrl, starterContext)
    thisLogger().info("Loading project from ${url}")

    return HttpRequests
      .post(url.toExternalForm(), HttpRequests.JSON_CONTENT_TYPE)
      .tuner {
        MachineIdManager.getAnonymizedMachineId("ij-plugin-generator")?.let { userId ->
          it.setRequestProperty("X-Machine-ID", userId)
        }
      }
      .userAgent(fullProductNameAndBuildVersion())
      .connectTimeout(10000)
      .isReadResponseOnError(true)
      .connect { request ->
        request.write(objectMapper.writeValueAsString(payload))
        handleDownloadResponse(request, tempFile, progressIndicator)
      }
  }

  private fun ArrayNode.addPacks() {
    add(getPluginType().packId)
    // add .gitignore even without "Create Git repository" checkbox  enabled, so the project is ready for sharing:
    add("org.jetbrains.intellij.platform.vcs/git")
    starterContext.dependencies.map { it.id }.forEach { add(it) }
  }

  private fun ObjectNode.putProperties() {
    if (getPluginType() == PluginType.PLUGIN) {
      if (starterContext.includeExamples) {
        put("$PLUGIN_TYPE_PACK_GROUP_ID/plugin/addSampleCode", "true")
      }
    }
  }

  private fun fullProductNameAndBuildVersion() =
    "${ApplicationNamesInfo.getInstance().fullProductName}/${ApplicationInfo.getInstance().build.asStringWithoutProductCode()}"

  override fun composeGeneratorUrl(serverUrl: String, starterContext: WebStarterContext): Url {
    return Urls.newFromEncoded(starterContext.serverUrl + "/api/generate/download")
  }

  @RequiresBackgroundThread
  override fun extractGeneratorResult(tempZipFile: File, contentEntryDir: File) {
    ZipUtil.extract(tempZipFile, contentEntryDir, null)
  }

  override fun createOptionsStep(contextProvider: WebStarterContextProvider): WebStarterInitialStep {
    return object : WebStarterInitialStep(contextProvider) {
      private val typeProperty: GraphProperty<PluginType> = propertyGraph.property(PluginType.PLUGIN)

      override fun addFieldsBefore(layout: Panel) {
        layout.row(message("module.builder.type")) {
          segmentedButton(listOf(PluginType.PLUGIN, PluginType.THEME)) { text = it.messagePointer.get() }
            .bind(typeProperty)
        }.bottomGap(BottomGap.SMALL)

        setPluginType(PluginType.PLUGIN)

        typeProperty.afterChange { pluginType ->
          setPluginType(pluginType)

          groupRow.visible(pluginType == PluginType.PLUGIN)
          artifactRow.visible(pluginType == PluginType.PLUGIN)

          fireStateChanged() // refresh the next step depending on a plugin type, skip dependencies step for THEME
        }
      }

      override fun addFieldsAfter(layout: Panel) {
        layout.row {
          hyperLink(message("module.builder.how.to.link"), "https://plugins.jetbrains.com/docs/intellij/intellij-platform.html")
        }
        layout.row {
          hyperLink(message("module.builder.github.template.link"), "https://jb.gg/plugin-template")
        }
      }

      override fun validate(): Boolean {
        if (!super.validate()) {
          return false
        }
        // for themes, the libraries step is skipped, so we need to trigger download here
        if (getPluginType() == PluginType.THEME && starterContext.result == null) {
          updateDataModel()
          validateAndDownloadProject(wizardContext.project)
          if (starterContext.result == null) {
            return false
          }
        }
        return true
      }
    }
  }

  override fun createLibrariesStep(contextProvider: WebStarterContextProvider): WebStarterLibrariesStep {
    return object : WebStarterLibrariesStep(contextProvider) {
      override fun isStepVisible(): Boolean {
        return getPluginType() == PluginType.PLUGIN
      }
    }
  }

  override fun isSuitableSdkType(sdkType: SdkTypeId): Boolean {
    return when (getPluginType()) {
      PluginType.PLUGIN -> super.isSuitableSdkType(sdkType)
      PluginType.THEME -> sdkType == IdeaJdk.getInstance()
    }
  }

  override fun setupModule(module: Module) {
    // disable aggressive error highlighting in plugin.xml
    module.project.putUserData(DEVKIT_NEWLY_GENERATED_PROJECT, true)

    super.setupModule(module)
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

      val contentEntry = MarkRootsManager.findContentEntry(modifiableRootModel, contentRoot)
      contentEntry?.addSourceFolder(VfsUtilCore.pathToUrl(resourceRootPath), JavaResourceRootType.RESOURCE)
    }
  }

  override fun getFilePathsToOpen(): List<String> {
    return when (getPluginType()) {
      PluginType.PLUGIN -> {
        listOf(
          "src/main/resources/META-INF/plugin.xml",
          "build.gradle.kts"
        )
      }

      PluginType.THEME -> {
        listOf(
          "resources/META-INF/plugin.xml",
          "resources/theme/${sanitizeThemeFilename(starterContext.artifact)}.theme.json"
        )
      }
    }
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

  override fun getCustomizedMessages(): CustomizedMessages {
    return CustomizedMessages().apply {
      dependenciesLabel = message("module.builder.web.features.label")
      selectedDependenciesLabel = message("module.builder.web.selected.features.label")
      noDependenciesSelectedLabel = message("module.builder.web.select.features.hint")
    }
  }

  override fun isAvailable(): Boolean {
    return Util.isEnabled()
  }

  object Util {
    fun isEnabled(): Boolean {
      return Registry.`is`("devkit.web.based.plugin.generator.enabled", false)
    }
  }

  enum class PluginType(
    val messagePointer: Supplier<String>,
    val packId: String,
  ) {
    PLUGIN(
      DevKitBundle.messagePointer("module.builder.type.plugin"),
      "org.jetbrains.intellij.platform/plugin"
    ),
    THEME(
      DevKitBundle.messagePointer("module.builder.type.theme"),
      "org.jetbrains.intellij.platform/theme"
    )
  }

  internal class PackCategory(
    title: String,
    val id: String,
    val extensions: MutableList<WebStarterDependency> = mutableListOf(),
  ) : WebStarterDependencyCategory(title, extensions)
}
