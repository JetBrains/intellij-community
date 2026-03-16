// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.pluginSystem.parser.impl.RawPluginDescriptor
import com.intellij.platform.pluginSystem.testFramework.LoadFromSourceXIncludeLoader
import com.intellij.platform.pluginSystem.testFramework.loadRawPluginDescriptorInTest
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

data class SimplifiedPluginModelBuilderOptions(
  val prefixesOfPathsIncludedFromLibrariesViaXiInclude: List<String> = emptyList(),

  /**
   * By default, files included via xi:include patterns are searched in 'META-INF', 'idea' and the root directory.
   * This property allows specifying custom patterns of directories where such files are searched.
   */
  val additionalPatternsOfDirectoriesContainingIncludedXmlFiles: List<String> = emptyList(),

  /**
   * Describes different core plugins (with ID `com.intellij`) located in the project sources.
   * All of them are checked, but only the first one is used when checking dependencies from other plugins.
   */
  val corePluginDescriptions: List<CorePluginDescription> = COMMUNITY_CORE_PLUGINS,

  /**
   * Set of modules containing `plugin.xml` files which should be ignored because they correspond to smaller editions of plugins,
   * and other module contains `plugin.xml` file with the same ID.
   * It's better to avoid such configurations, and include all optional parts as content modules in a single `plugin.xml`.
   */
  val mainModulesOfAlternativePluginVariants: Set<String> = emptySet(),

  /**
   * Set of modules where a descriptor file named after the module is placed in META-INF directory, not in the resource root.
   */
  val modulesWithIncorrectlyPlacedModuleDescriptor: Set<String> = emptySet(),

  val pluginVariantsWithDynamicIncludes: List<PluginVariantWithDynamicIncludes> = emptyList(),
)

sealed interface DescriptorFileInfo {
  val sourceModule: JpsModule
  val descriptorFile: Path
  val descriptor: RawPluginDescriptor
}

internal data class ContentModuleDescriptorFileInfo(
  val contentModuleName: String,
  override val sourceModule: JpsModule,
  override val descriptorFile: Path,
  override val descriptor: RawPluginDescriptor,
) : DescriptorFileInfo

internal data class PluginDescriptorFileInfo(
  override val sourceModule: JpsModule,
  override val descriptorFile: Path,
  override val descriptor: RawPluginDescriptor,
  val inTests: Boolean,
) : DescriptorFileInfo

data class SimplifiedPluginModelBuilderError(
  val message: String,
  val sourceModule: JpsModule,
  val params: Map<String, Any?> = mapOf(),
)

data class SimplifiedPluginModel(
  val descriptorFileInfos: List<DescriptorFileInfo>,
  val moduleNameToInfo: HashMap<String, ModuleInfo>,
  val allMainModulesOfPlugins: ArrayList<ModuleInfo>,
  val pluginIdToInfo: HashMap<String, ModuleInfo>,
  val pluginAliases: HashSet<String>,
  val errors: List<SimplifiedPluginModelBuilderError>,
)

class SimplifiedPluginModelBuilder(
  private val project: JpsProject,
  private val builderOptions: SimplifiedPluginModelBuilderOptions,
) {
  private val errors = mutableListOf<SimplifiedPluginModelBuilderError>()

  private val xIncludeLoader = LoadFromSourceXIncludeLoader(
    prefixesOfPathsIncludedFromLibrariesViaXiInclude = builderOptions.prefixesOfPathsIncludedFromLibrariesViaXiInclude,
    project = project,
    parentDirectoriesPatterns = listOf("META-INF", "idea", "") + builderOptions.additionalPatternsOfDirectoriesContainingIncludedXmlFiles,
  )

  companion object {
    internal fun createModuleFileInfo(
      contentModuleDescriptorFileInfo: ContentModuleDescriptorFileInfo,
      moduleName: String,
      moduleNameToInfo: MutableMap<String, ModuleInfo>,
    ): ModuleInfo {
      val moduleDescriptor = contentModuleDescriptorFileInfo.descriptor

      val moduleInfo = ModuleInfo(
        pluginId = null,
        name = moduleName,
        sourceModule = contentModuleDescriptorFileInfo.sourceModule,
        descriptorFile = contentModuleDescriptorFileInfo.descriptorFile,
        packageName = moduleDescriptor.`package`,
        descriptor = moduleDescriptor,
      )
      moduleNameToInfo[moduleName] = moduleInfo
      return moduleInfo
    }
  }

  private fun reportError(message: String, sourceModule: JpsModule, params: Map<String, Any?> = mapOf()) {
    errors.add(SimplifiedPluginModelBuilderError(message, sourceModule, params))
  }

  fun buildSimplifiedPluginModel(): SimplifiedPluginModel {
    // 1. collect plugin and module file info set
    val descriptorFileInfos = project.modules.flatMap { module ->
      try {
        findPluginAndModuleDescriptors(module)
      }
      catch (e: Exception) {
        reportError("Failed to load descriptor for '${module.name}': ${e.message}", sourceModule = module)
        return@flatMap emptyList()
      }
    }

    val pluginAliases = HashSet<String>()
    descriptorFileInfos.flatMapTo(pluginAliases) { it.descriptor.pluginAliases }

    val moduleNameToInfo = HashMap<String, ModuleInfo>()

    descriptorFileInfos.filterIsInstance<ContentModuleDescriptorFileInfo>().forEach { moduleDescriptorFileInfo ->
      createModuleFileInfo(
        contentModuleDescriptorFileInfo = moduleDescriptorFileInfo,
        moduleName = moduleDescriptorFileInfo.sourceModule.name,
        moduleNameToInfo = moduleNameToInfo,
      )
    }
    val alternativeCorePluginMainModules = builderOptions.corePluginDescriptions.drop(1).mapTo(HashSet()) {
      it.mainModuleName
    }

    // 2. process plugins - process content to collect modules
    val allMainModulesOfPlugins = ArrayList<ModuleInfo>()
    val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()
    descriptorFileInfos.filterIsInstance<PluginDescriptorFileInfo>().forEach { pluginDescriptorFileInfo ->
      val id = pluginDescriptorFileInfo.descriptor.id
               ?: pluginDescriptorFileInfo.descriptor.name
      if (id == null) {
        reportError(
          message = "Plugin id is not specified",
          sourceModule = pluginDescriptorFileInfo.sourceModule,
          params = mapOf(
            "descriptorFile" to pluginDescriptorFileInfo.descriptorFile,
          ),
        )
        return@forEach
      }

      val moduleInfo = ModuleInfo(
        pluginId = id,
        name = null,
        sourceModule = pluginDescriptorFileInfo.sourceModule,
        descriptorFile = pluginDescriptorFileInfo.descriptorFile,
        packageName = pluginDescriptorFileInfo.descriptor.`package`,
        descriptor = pluginDescriptorFileInfo.descriptor,
      )
      allMainModulesOfPlugins.add(moduleInfo)
      val sourceModuleName = pluginDescriptorFileInfo.sourceModule.name
      if (sourceModuleName !in alternativeCorePluginMainModules && sourceModuleName !in builderOptions.mainModulesOfAlternativePluginVariants) {
        val prev = pluginIdToInfo.put(id, moduleInfo)
        // todo how do we can exclude it automatically
        if (prev != null) {
          reportError(
            "Duplicated plugin id: $id",
            pluginDescriptorFileInfo.sourceModule,
            mapOf(
              "prev" to prev,
              "current" to moduleInfo,
            ),
          )
        }
      }
    }

    for (pluginVariant in builderOptions.pluginVariantsWithDynamicIncludes) {
      PlatformTestUtil.withSystemProperty<Throwable>(pluginVariant.systemPropertyName, pluginVariant.systemPropertyValue) {
        val sourceModule = project.findModuleByName(pluginVariant.mainModuleName)
                           ?: error("Cannot find source module '${pluginVariant.mainModuleName}' specified in 'pluginVariantsWithDynamicIncludes'")
        val pluginModuleInfo = findPluginAndModuleDescriptors(sourceModule).filterIsInstance<PluginDescriptorFileInfo>().singleOrNull()
        if (pluginModuleInfo == null) {
          reportError("Failed to load descriptor for '${sourceModule.name}'", sourceModule)
          return@withSystemProperty
        }

        val pluginDescriptor = pluginModuleInfo.descriptor
        val pluginDescriptorFile = pluginModuleInfo.descriptorFile

        allMainModulesOfPlugins.add(ModuleInfo(
          pluginId = pluginDescriptor.id,
          name = pluginVariant.mainModuleName,
          sourceModule = sourceModule,
          descriptorFile = pluginDescriptorFile,
          packageName = pluginDescriptor.`package`,
          descriptor = pluginDescriptor,
        ))
      }
    }
    return SimplifiedPluginModel(descriptorFileInfos, moduleNameToInfo, allMainModulesOfPlugins, pluginIdToInfo, pluginAliases, errors)
  }

  private fun loadRawPluginDescriptor(file: Path): RawPluginDescriptor? {
    if (Files.notExists(file)) {
      return null
    }

    return loadRawPluginDescriptorInTest(file, xIncludeLoader)
  }

  private fun findPluginAndModuleDescriptors(module: JpsModule): List<DescriptorFileInfo> {
    if (module.name !in builderOptions.modulesWithIncorrectlyPlacedModuleDescriptor) {
      for (sourceRoot in module.sourceRoots) {
        val moduleXml = findFile(sourceRoot, "META-INF/${module.name}.xml")
        if (moduleXml != null) {
          reportError(
            "Module descriptor must be in the root of module root",
            module,
            mapOf(
              "module" to module.name,
              "moduleDescriptor" to moduleXml,
            ),
          )
        }
      }
    }

    val customRootPluginXmlFileName = builderOptions.corePluginDescriptions.find { it.mainModuleName == module.name }?.rootPluginXmlName
    val pluginFileName = customRootPluginXmlFileName ?: "plugin.xml"

    val (productionPluginDescriptors, testPluginDescriptors) =
      module.sourceRoots.mapNotNull { sourceRoot ->
        val pluginDescriptorFile = findFile(sourceRoot, "META-INF/$pluginFileName") ?: return@mapNotNull null
        val descriptor = loadRawPluginDescriptor(pluginDescriptorFile) ?: return@mapNotNull null
        PluginDescriptorFileInfo(
          sourceModule = module,
          descriptorFile = pluginDescriptorFile,
          descriptor = descriptor,
          inTests = sourceRoot.rootType.isForTests,
        )
      }
        .partition { !it.inTests }

    if (customRootPluginXmlFileName != null && productionPluginDescriptors.isEmpty()) {
      reportError(
        message = "Cannot find $customRootPluginXmlFileName in ${module.name}",
        sourceModule = module,
      )
    }

    val moduleDescriptors = module.sourceRoots.flatMap { sourceRoot ->
      sourceRoot.path.listDirectoryEntries("*.xml")
        .filter { it.nameWithoutExtension == module.name || it.nameWithoutExtension.startsWith("${module.name}.") }
        .mapNotNull { moduleDescriptorFile ->
          val descriptor = loadRawPluginDescriptor(moduleDescriptorFile) ?: return@mapNotNull null
          val contentModuleName = when {
            moduleDescriptorFile.nameWithoutExtension.removeSuffix("._test") == module.name -> moduleDescriptorFile.nameWithoutExtension
            else -> "${module.name}/${moduleDescriptorFile.nameWithoutExtension.removePrefix("${module.name}.")}"
          }
          ContentModuleDescriptorFileInfo(
            contentModuleName = contentModuleName,
            sourceModule = module,
            descriptorFile = moduleDescriptorFile,
            descriptor = descriptor,
          )
        }
    }

    for (pluginDescriptors in listOf(productionPluginDescriptors, testPluginDescriptors)) {
      if (pluginDescriptors.size > 1) {
        reportError(
          "Duplicated plugin.xml",
          module,
          mapOf(
            "module" to module.name,
            "firstPluginDescriptor" to pluginDescriptors[0].descriptorFile,
            "secondPluginDescriptor" to pluginDescriptors[1].descriptorFile,
          ),
        )
      }
    }

    return productionPluginDescriptors + testPluginDescriptors + moduleDescriptors
  }

  private fun findFile(root: JpsModuleSourceRoot, relativePath: String): Path? {
    return JpsJavaExtensionService.getInstance().findSourceFile(root, relativePath)
  }
}
