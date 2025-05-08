// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.RawPluginDescriptor
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.elements.ContentElement
import com.intellij.platform.plugins.parser.impl.elements.DependenciesElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.NamedFailure
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.xml.dom.NoOpXmlInterner
import com.intellij.util.xml.dom.XmlInterner
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.opentest4j.MultipleFailuresError
import java.io.StringWriter
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

data class CorePluginDescription(
  val mainModuleName: String,
  val rootPluginXmlName: String = "plugin.xml"
)

val COMMUNITY_CORE_PLUGINS = listOf(
  CorePluginDescription(mainModuleName = "intellij.idea.community.customization", rootPluginXmlName = "IdeaPlugin.xml"),
  CorePluginDescription(mainModuleName = "intellij.pycharm.community", rootPluginXmlName = "PyCharmCorePlugin.xml"),
)

/**
 * Defines a variant of a plugin with a custom value of system property used in `includeUnless`/`includeIf` directives. 
 */
data class PluginVariantWithDynamicIncludes(
  val mainModuleName: String,
  val systemPropertyName: String,
  val systemPropertyValue: String,
)

data class PluginValidationOptions(
  val skipUnresolvedOptionalContentModules: Boolean = false,
  val reportDependsTagInPluginXmlWithPackageAttribute: Boolean = true,
  val referencedPluginIdsOfExternalPlugins: Set<String> = emptySet(),
  val pathsIncludedFromLibrariesViaXiInclude: Set<String> = emptySet(),

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

fun validatePluginModel(projectPath: Path, validationOptions: PluginValidationOptions = PluginValidationOptions()): PluginValidationResult {
  val project = IntelliJProjectConfiguration.loadIntelliJProject(projectPath.toString())
  return validatePluginModel(project, projectPath, validationOptions)
}

fun validatePluginModel(project: JpsProject, projectHomePath: Path,
                        validationOptions: PluginValidationOptions = PluginValidationOptions()): PluginValidationResult {
  return PluginModelValidator(project, projectHomePath, validationOptions).validate()
}

class PluginValidationResult internal constructor(
  private val validationErrors: List<PluginValidationError>,
  private val pluginIdToInfo: Map<String, ModuleInfo>,
) {
  val errors: List<Throwable>
    get() = java.util.List.copyOf(validationErrors)

  val namedFailures: List<NamedFailure>
    get() {
      return validationErrors.groupBy { it.sourceModule.name }.map { (name, errors) ->
        NamedFailure(name, errors.singleOrNull() ?: MultipleFailuresError("${errors.size} failures", errors))
      }
    }

  fun graphAsString(projectHomePath: Path): CharSequence {
    val stringWriter = StringWriter()
    val writer = JsonFactory().createGenerator(stringWriter)
    writer.useDefaultPrettyPrinter()
    writer.use {
      writer.obj {
        val entries = pluginIdToInfo.entries.toMutableList()
        entries.sortBy { it.value.sourceModule.name }
        for (entry in entries) {
          val item = entry.value
          if (item.packageName == null && !hasContentOrDependenciesInV2Format(item.descriptor)) {
            continue
          }

          writer.writeFieldName(item.sourceModule.name)
          writeModuleInfo(writer, item, projectHomePath)
        }
      }
    }
    return stringWriter.buffer
  }

  fun writeGraph(outFile: Path, projectHomePath: Path) {
    PluginGraphWriter(pluginIdToInfo, projectHomePath).write(outFile)
  }
}

class PluginModelValidator(
  private val project: JpsProject,
  private val projectHomePath: Path,
  private val validationOptions: PluginValidationOptions
) {
  private val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()
  private val pluginAliases = HashSet<String>()
  private val _errors = mutableListOf<PluginValidationError>()
  private val xIncludeLoader =
    LoadFromSourceXIncludeLoader(
      pathsIncludedFromLibrariesViaXiInclude = validationOptions.pathsIncludedFromLibrariesViaXiInclude, 
      project = project,
      directoriesToIndex = listOf("META-INF", "idea", ""),
    )

  fun validate(): PluginValidationResult {
    // 1. collect plugin and module file info set
    val moduleDescriptorFileInfos = project.modules.asSequence()
      .mapNotNull { module ->
        try {
          createFileInfo(module)
        }
        catch (e: Exception) {
          reportError("Failed to load descriptor for '${module.name}': ${e.message}", sourceModule = module)
          return@mapNotNull null
        }
      }
    
    val sourceModuleNameToFileInfo = moduleDescriptorFileInfos.associateBy { it.sourceModule.name }
    moduleDescriptorFileInfos.flatMapTo(pluginAliases) {
      it.pluginDescriptor?.pluginAliases ?: emptySet()
    } 
    moduleDescriptorFileInfos.flatMapTo(pluginAliases) {
      it.moduleDescriptor?.pluginAliases ?: emptySet()
    } 

    val moduleNameToInfo = HashMap<String, ModuleInfo>()

    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      checkModuleFileInfo(
        moduleDescriptorFileInfo = moduleMetaInfo,
        moduleName = sourceModuleName,
        moduleNameToInfo = moduleNameToInfo,
      )
    }
    val alternativeCorePluginMainModules = validationOptions.corePluginDescriptions.drop(1).mapTo(HashSet()) {
      it.mainModuleName
    }

    // 2. process plugins - process content to collect modules
    val allMainModulesOfPlugins = ArrayList<ModuleInfo>()
    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      // interested only in plugins
      val descriptor = moduleMetaInfo.pluginDescriptor ?: continue
      val descriptorFile = moduleMetaInfo.pluginDescriptorFile ?: continue

      val id = descriptor.id
               ?: descriptor.name
      if (id == null) {
        reportError(
          "Plugin id is not specified",
          moduleMetaInfo.sourceModule,
          mapOf(
            "descriptorFile" to descriptorFile
          ),
        )
        continue
      }

      val moduleInfo = ModuleInfo(
        pluginId = id,
        name = null,
        sourceModule = moduleMetaInfo.sourceModule,
        descriptorFile = descriptorFile,
        packageName = descriptor.`package`,
        descriptor = descriptor,
      )
      allMainModulesOfPlugins.add(moduleInfo)
      if (sourceModuleName !in alternativeCorePluginMainModules && sourceModuleName !in validationOptions.mainModulesOfAlternativePluginVariants) {
        val prev = pluginIdToInfo.put(id, moduleInfo)
        // todo how do we can exclude it automatically
        if (prev != null) {
          reportError(
            "Duplicated plugin id: $id",
            moduleMetaInfo.sourceModule,
            mapOf(
              "prev" to prev,
              "current" to moduleInfo,
            ),
          )
        }
      }
    }

    for (pluginVariant in validationOptions.pluginVariantsWithDynamicIncludes) {
      PlatformTestUtil.withSystemProperty<Throwable>(pluginVariant.systemPropertyName, pluginVariant.systemPropertyValue) {
        val sourceModule = sourceModuleNameToFileInfo[pluginVariant.mainModuleName]?.sourceModule
                           ?: error("Cannot find source module '${pluginVariant.mainModuleName}' specified in 'pluginVariantsWithDynamicIncludes'")
        val pluginModuleInfo = createFileInfo(sourceModule)
        if (pluginModuleInfo == null) {
          reportError("Failed to load descriptor for '${sourceModule.name}'", sourceModule)
          return@withSystemProperty
        }
        
        val pluginDescriptor = pluginModuleInfo.pluginDescriptor
        val pluginDescriptorFile = pluginModuleInfo.pluginDescriptorFile
        if (pluginDescriptor == null || pluginDescriptorFile == null) {
          reportError("Plugin descriptor is not found in '${pluginModuleInfo.sourceModule.name}'", sourceModule)
          return@withSystemProperty
        }

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
    
    for (pluginInfo in allMainModulesOfPlugins) {
      checkContent(
        contentElements = pluginInfo.descriptor.contentModules,
        referencingModuleInfo = pluginInfo,
        sourceModuleNameToFileInfo = sourceModuleNameToFileInfo,
        moduleNameToInfo = moduleNameToInfo,
      )
    }

    val registeredContentModules = allMainModulesOfPlugins.flatMapTo(HashSet()) { pluginInfo ->
      pluginInfo.content.mapNotNull { it.name } 
    }

    // 3. check dependencies - we are aware about all modules now
    for (pluginInfo in allMainModulesOfPlugins) {
      val descriptor = pluginInfo.descriptor

      for (incompatibleWithId in descriptor.incompatibleWith) {
        if (incompatibleWithId !in pluginIdToInfo && incompatibleWithId !in pluginAliases 
            && incompatibleWithId !in validationOptions.referencedPluginIdsOfExternalPlugins) {
          reportError("'incompatible-with' refers to unknown plugin '$incompatibleWithId'", pluginInfo.sourceModule,
                      mapOf("descriptorFile" to pluginInfo.descriptorFile))
        }
      }
      
      checkDependencies(descriptor.dependencies, pluginInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo,
                        registeredContentModules)

      // in the end, after processing content and dependencies
      if (validationOptions.reportDependsTagInPluginXmlWithPackageAttribute && pluginInfo.packageName != null) {
        descriptor.depends.firstOrNull { !it.isOptional }?.let {
          reportError(
            "The old format should not be used for a plugin with the specified package prefix (${pluginInfo.packageName}), but `depends` tag is used." +
            " Please use the new format (see https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md#the-dependencies-element)",
            pluginInfo.sourceModule,
            mapOf(
              "descriptorFile" to pluginInfo.descriptorFile,
              "depends" to it,
            ),
          )
        }
      }

      for (contentModuleInfo in pluginInfo.content) {
        checkDependencies(
          dependenciesElements = contentModuleInfo.descriptor.dependencies,
          referencingModuleInfo = contentModuleInfo,
          referencingPluginInfo = pluginInfo,
          moduleNameToInfo = moduleNameToInfo,
          sourceModuleNameToFileInfo = sourceModuleNameToFileInfo,
          registeredContentModules = registeredContentModules,
        )

        if (contentModuleInfo.descriptor.depends.isNotEmpty()) {
          reportError(
            "Old format must be not used for a module but `depends` tag is used",
            pluginInfo.sourceModule,
            mapOf(
              "descriptorFile" to contentModuleInfo.descriptorFile,
              "depends" to contentModuleInfo.descriptor.depends.first(),
            ),
          )
        }
      }
    }

    return PluginValidationResult(_errors, pluginIdToInfo)
  }

  private fun checkDependencies(
    dependenciesElements: List<DependenciesElement>,
    referencingModuleInfo: ModuleInfo,
    referencingPluginInfo: ModuleInfo,
    moduleNameToInfo: Map<String, ModuleInfo>,
    sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>,
    registeredContentModules: Set<String>
  ) {
    val moduleDependenciesCount = dependenciesElements.count { 
      it is DependenciesElement.ModuleDependency || it is DependenciesElement.PluginDependency && it.pluginId.startsWith("com.intellij.modules.")
    }
    
    for (child in dependenciesElements) {

      fun registerError(message: String, fix: String? = null) {
        reportError(
          message,
          referencingModuleInfo.sourceModule,
          mapOf(
            "entry" to child,
            "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
          ),
          fix
        )
      }

      when (child) {
        is DependenciesElement.PluginDependency -> {
          // todo check that the referenced plugin exists
          val id = child.pluginId
          if (id == "com.intellij.modules.java") {
            registerError("Use com.intellij.java id instead of com.intellij.modules.java")
            continue
          }
          if (id == "com.intellij.modules.platform") {
            // todo: remove this check when MP-7413 is fixed in the plugin verifier version used at the Marketplace
            if (moduleDependenciesCount > 1) {
              registerError("No need to specify dependency on $id")
            }
            continue
          }
          if (id == referencingPluginInfo.pluginId) {
            //todo: uncomment and fix violations
            //registerError("Do not add dependency on a parent plugin")
            continue
          }

          val dependency = pluginIdToInfo[id]
          if (dependency == null 
              && id !in validationOptions.referencedPluginIdsOfExternalPlugins
              && id !in pluginAliases) {
            registerError("Plugin not found: $id")
            continue
          }

          val ref = Reference(
            name = id,
            isPlugin = true,
            moduleInfo = dependency
          )
          if (referencingModuleInfo.dependencies.contains(ref)) {
            registerError("Referencing module dependencies contains $id: $id")
            continue
          }
          referencingModuleInfo.dependencies.add(ref)
          continue
        }
        is DependenciesElement.ModuleDependency -> {
          val moduleName = child.moduleName
          val moduleInfo = moduleNameToInfo.get(moduleName)
          if (moduleInfo == null) {
            val moduleDescriptorFileInfo = sourceModuleNameToFileInfo.get(moduleName)
            if (moduleDescriptorFileInfo != null) {
              if (moduleDescriptorFileInfo.pluginDescriptor != null) {
                registerError(
                  message = "Dependency on plugin must be specified using `plugin` and not `module`",
                  fix = """
                        Change dependency element to:
                        
                        <plugin id="${moduleDescriptorFileInfo.pluginDescriptor.id}"/>
                      """,
                )
                continue
              }
            }
            registerError("Module not found: $moduleName")
            continue
          }
          
          if (moduleName !in registeredContentModules) {
            registerError("Module '$moduleName' is not registered as a content module, but used as a dependency." +
                          "Either convert it to a content module, or use dependency on the plugin which includes it instead.")
            continue
          }

          referencingModuleInfo.dependencies.add(Reference(moduleName, isPlugin = false, moduleInfo))

          for (dependsElement in referencingModuleInfo.descriptor.depends) {
            if (dependsElement.configFile?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
              registerError("Module, that used as dependency, must be not specified in `depends`")
              break
            }
          }
        }
      }
    }
  }

  // For plugin two variants:
  // 1) depends + dependency on plugin in a referenced descriptor = optional descriptor. In old format: depends tag
  // 2) no depends + no dependency on plugin in a referenced descriptor = directly injected into plugin (separate classloader is not created
  // during a transition period). In old format: xi:include (e.g. <xi:include href="dockerfile-language.xml"/>).
  private fun checkContent(
    contentElements: List<ContentElement>,
    referencingModuleInfo: ModuleInfo,
    sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>,
    moduleNameToInfo: MutableMap<String, ModuleInfo>
  ) {
    for (contentElement in contentElements) {
      contentElement as ContentElement.Module
      fun registerError(message: String, additionalParams: Map<String, Any?> = emptyMap()) {
        reportError(
          message,
          referencingModuleInfo.sourceModule,
          mapOf(
            "entry" to contentElement,
            "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
          ) + additionalParams,
        )
      }

      val moduleName = contentElement.name

      if (moduleName == "intellij.platform.commercial.verifier") {
        registerError("intellij.platform.commercial.verifier is not supposed to be used as content of plugin")
        continue
      }

      // ignore null - getModule reports error
      val moduleDescriptorFileInfo = getModuleDescriptorFileInfo(
        moduleName = moduleName,
        moduleLoadingRule = contentElement.loadingRule,
        referencingModuleInfo = referencingModuleInfo,
        sourceModuleNameToFileInfo = sourceModuleNameToFileInfo
      )
      if (moduleDescriptorFileInfo == null) {
        continue
      }

      val moduleDescriptor = moduleDescriptorFileInfo.moduleDescriptor
      if (moduleDescriptor == null) {
        registerError("No module descriptor ($moduleDescriptorFileInfo)")
        continue
      }
      val moduleInfo = checkModuleFileInfo(moduleDescriptorFileInfo, moduleName, moduleNameToInfo) ?: continue
      referencingModuleInfo.content.add(moduleInfo)

      // check that not specified using the "depends" tag
      for (dependsElement in referencingModuleInfo.descriptor.depends) {
        if (dependsElement.configFile?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          registerError(
            "Module must be not specified in `depends`.",
            mapOf(
              "referencedDescriptorFile" to moduleInfo.descriptorFile
            ),
          )
          continue
        }
      }

      if (moduleDescriptor.contentModules.isNotEmpty()) {
        registerError(
          "Module cannot define content",
          mapOf(
            "referencedDescriptorFile" to moduleInfo.descriptorFile
          )
        )
      }
    }
  }

  private fun checkModuleFileInfo(
    moduleDescriptorFileInfo: ModuleDescriptorFileInfo,
    moduleName: String,
    moduleNameToInfo: MutableMap<String, ModuleInfo>,
  ): ModuleInfo? {
    val moduleDescriptor = moduleDescriptorFileInfo.moduleDescriptor ?: return null

    val moduleInfo = ModuleInfo(
      pluginId = null,
      name = moduleName,
      sourceModule = moduleDescriptorFileInfo.sourceModule,
      descriptorFile = moduleDescriptorFileInfo.moduleDescriptorFile!!,
      packageName = moduleDescriptor.`package`,
      descriptor = moduleDescriptor,
    )
    moduleNameToInfo.put(moduleName, moduleInfo)
    return moduleInfo
  }

  private fun getModuleDescriptorFileInfo(
    moduleName: String,
    moduleLoadingRule: ModuleLoadingRule,
    referencingModuleInfo: ModuleInfo,
    sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>
  ): ModuleDescriptorFileInfo? {
    var module = sourceModuleNameToFileInfo.get(moduleName.removeSuffix("._test"))
    if (module != null) {
      return module
    }

    val containingModuleName = moduleName.substringBefore('/')
    module = sourceModuleNameToFileInfo[containingModuleName]
    if (module == null) {
      if (moduleLoadingRule == ModuleLoadingRule.REQUIRED || moduleLoadingRule == ModuleLoadingRule.EMBEDDED || !validationOptions.skipUnresolvedOptionalContentModules) {
        reportError("Cannot find module $containingModuleName", referencingModuleInfo.sourceModule, mapOf(
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile
        ))
      }
      return null
    }

    val fileName = "${moduleName.replace('/', '.')}.xml"
    val result = loadFileInModule(sourceModule = module.sourceModule, fileName = fileName)
    if (result == null) {
      val resourceRootPath = module.sourceModule.getSourceRoots(JavaResourceRootType.RESOURCE).firstOrNull()?.path
      reportError(
        message = "Module ${module.sourceModule.name} doesn't have descriptor file",
        sourceModule = referencingModuleInfo.sourceModule,
        params = mapOf(
          "expectedFile" to fileName,
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        ),
        fix = resourceRootPath?.let { """
              Create file $fileName in ${projectHomePath.relativize(resourceRootPath).invariantSeparatorsPathString}
              with content:
              
              <idea-plugin package="REPLACE_BY_MODULE_PACKAGE">
              </idea-plugin>
            """
        }
      )
    }
    return result
  }

  private fun createFileInfo(module: JpsModule): ModuleDescriptorFileInfo? {
    if (module.name !in validationOptions.modulesWithIncorrectlyPlacedModuleDescriptor) {
      for (sourceRoot in module.productionSourceRoots) {
        val moduleXml = sourceRoot.findFile("META-INF/${module.name}.xml")
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

    val customRootPluginXmlFileName = validationOptions.corePluginDescriptions.find { it.mainModuleName == module.name }?.rootPluginXmlName
    val pluginFileName = customRootPluginXmlFileName ?: "plugin.xml"

    val pluginDescriptors =
      module.productionSourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val pluginDescriptorFile = sourceRoot.findFile("META-INF/$pluginFileName") ?: return@mapNotNullTo null 
        loadRawPluginDescriptor(pluginDescriptorFile)?.let { pluginDescriptorFile to it }
      }

    if (customRootPluginXmlFileName != null && pluginDescriptors.isEmpty()) {
      reportError(
        message = "Cannot find $customRootPluginXmlFileName in ${module.name}",
        sourceModule = module,
      )
    }
    
    val moduleDescriptors =
      module.productionSourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val moduleDescriptorFile = sourceRoot.findFile("${module.name}.xml") ?: return@mapNotNullTo null
        loadRawPluginDescriptor(moduleDescriptorFile)?.let { moduleDescriptorFile to it }
      }

    if (pluginDescriptors.size > 1) {
      reportError(
        "Duplicated plugin.xml",
        module,
        mapOf(
          "module" to module.name,
          "firstPluginDescriptor" to pluginDescriptors[0].first,
          "secondPluginDescriptor" to pluginDescriptors[1].first,
        ),
      )
      return null
    }
    if (moduleDescriptors.size > 1) {
      reportError(
        "Duplicated module descriptor",
        module,
        mapOf(
          "module" to module.name,
          "firstDescriptor" to moduleDescriptors[0].first,
          "secondDescriptor" to moduleDescriptors[1].first,
        )
      )
    }

    val testModuleDescriptors =
      module.testSourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val moduleDescriptorFile = sourceRoot.findFile("${module.name}._test.xml") ?: return@mapNotNullTo null
        loadRawPluginDescriptor(moduleDescriptorFile)?.let { moduleDescriptorFile to it }
      }

    val moduleDescriptorWithFile = (moduleDescriptors + testModuleDescriptors).firstOrNull()
    val moduleDescriptorFile = moduleDescriptorWithFile?.first
    val moduleDescriptor = moduleDescriptorWithFile?.second
    val pluginDescriptorFile = pluginDescriptors.singleOrNull()?.first
    val pluginDescriptor = pluginDescriptors.singleOrNull()?.second
    
    //todo: this is violated in some modules; maybe we should extract plugin.xml to a separate module and uncomment this.
    /*
    if (pluginDescriptorFile != null && moduleDescriptorFile != null) {
      reportError(
        "Module cannot have both plugin.xml and module descriptor",
        module,
        mapOf(
          "module" to module.name,
          "pluginDescriptor" to pluginDescriptorFile,
          "moduleDescriptor" to moduleDescriptorFile,
        ),
      ))
    }
    */

    return ModuleDescriptorFileInfo(
      sourceModule = module,
      moduleDescriptor = moduleDescriptor,
      moduleDescriptorFile = moduleDescriptorFile,
      pluginDescriptor = pluginDescriptor,
      pluginDescriptorFile = pluginDescriptorFile,
    )
  }

  private fun loadRawPluginDescriptor(file: Path): RawPluginDescriptor? {
    if (!file.exists()) return null
    
    val xmlInput = createNonCoalescingXmlStreamReader(file.inputStream(), file.pathString)
    val rawPluginDescriptor = PluginDescriptorFromXmlStreamConsumer(ValidationReadModuleContext, xIncludeLoader).let {
      it.consume(xmlInput)
      it.build()
    }
    
    return rawPluginDescriptor
  }

  private fun loadFileInModule(sourceModule: JpsModule, fileName: String): ModuleDescriptorFileInfo? {
    for (sourceRoot in sourceModule.productionSourceRoots) {
      val moduleDescriptorFile = sourceRoot.findFile(fileName) ?: continue
      val moduleDescriptor = loadRawPluginDescriptor(moduleDescriptorFile) ?: continue
      return ModuleDescriptorFileInfo(
        sourceModule = sourceModule,
        moduleDescriptor = moduleDescriptor,
        moduleDescriptorFile = moduleDescriptorFile,
      )
    }
    return null
  }

  private fun reportError(
    message: String,
    sourceModule: JpsModule,
    params: Map<String, Any?> = mapOf(),
    fix: String? = null,
  ) {
    _errors.add(PluginValidationError(
      params.entries.joinToString(
        prefix = "$message (\n  ",
        separator = ",\n  ",
        postfix = "\n)" + (fix?.let { "\n\nProposed fix:\n\n" + fix.trimIndent() + "\n\n" } ?: "")
      ) {
        it.key + "=" + paramValueToString(it.value)
      },
      sourceModule
    ))
  }

  private fun paramValueToString(value: Any?): String {
    return when (value) {
      is Path -> projectHomePath.relativize(value).invariantSeparatorsPathString
      else -> value.toString()
    }
  }

  private object ValidationReadModuleContext : ReadModuleContext {
    override val interner: XmlInterner
      get() = NoOpXmlInterner
    override val elementOsFilter: (OS) -> Boolean
      get() = { true }
  }
}

private class LoadFromSourceXIncludeLoader(
  private val pathsIncludedFromLibrariesViaXiInclude: Set<String>, 
  private val project: JpsProject,
  private val directoriesToIndex: List<String>,
) : XIncludeLoader {
  private val shortXmlPathToFullPaths = collectXmlFilesInIndexedDirectories()

  private fun collectXmlFilesInIndexedDirectories(): Map<String, List<Path>> {
    val shortNameToPaths = LinkedHashMap<String, MutableList<Path>>()
    for (module in project.modules) {
      for (sourceRoot in module.productionSourceRoots) {
        for (directoryName in directoriesToIndex) {
          val directory = if (directoryName == "") sourceRoot.path else sourceRoot.path.resolve(directoryName)
          if (directory.isDirectory()) {
            val prefix = if (directoryName == "") "" else "$directoryName/" 
            for (xmlFile in directory.listDirectoryEntries("*.xml")) {
              val shortPath = "$prefix${xmlFile.name}"
              if (shortPath == "META-INF/plugin.xml") {
                continue
              }
              shortNameToPaths.computeIfAbsent(shortPath) { ArrayList() }.add(xmlFile)
            }
          }
        }
      }
    }
    return shortNameToPaths
  }

  override fun loadXIncludeReference(path: String): XIncludeLoader.LoadedXIncludeReference? {
    if (path in pathsIncludedFromLibrariesViaXiInclude 
        || path.startsWith("META-INF/tips-")
        || path.startsWith("com/intellij/database/dialects/") //contains many files which slow down tests
        || path.startsWith("com/intellij/sql/dialects/") //contains many files which slow down tests
    ) {
      //todo: support loading from libraries
      return XIncludeLoader.LoadedXIncludeReference("<idea-plugin/>".byteInputStream(), "dummy tag for external $path")
    }
    val directoryName = path.substringBeforeLast(delimiter = '/', missingDelimiterValue = "")
    val files = if (directoryName in directoriesToIndex) {
      shortXmlPathToFullPaths[path] ?: emptyList()
    }
    else {
      project.modules.asSequence()
        .flatMap { it.productionSourceRoots }
        .mapNotNullTo(ArrayList()) { it.findFile(path) }
        .filterTo(ArrayList()) { it.exists() }
    }
    val file = files.firstOrNull()
    if (file != null) {
      return XIncludeLoader.LoadedXIncludeReference(file.inputStream(), file.pathString)
    }
    return null
  }
}

internal data class ModuleInfo(
  @JvmField val pluginId: String?,
  @JvmField val name: String?,
  @JvmField val sourceModule: JpsModule,
  @JvmField val descriptorFile: Path,
  @JvmField val packageName: String?,

  @JvmField val descriptor: RawPluginDescriptor,
) {
  @JvmField
  val content = mutableListOf<ModuleInfo>()
  @JvmField
  val dependencies = mutableListOf<Reference>()

  val isPlugin: Boolean
    get() = pluginId != null
}

internal data class Reference(@JvmField val name: String, @JvmField val isPlugin: Boolean, @JvmField val moduleInfo: ModuleInfo?)

private data class ModuleDescriptorFileInfo(
  @JvmField val sourceModule: JpsModule,

  @JvmField val moduleDescriptor: RawPluginDescriptor? = null,
  @JvmField val moduleDescriptorFile: Path? = null,
  @JvmField val pluginDescriptorFile: Path? = null,
  @JvmField val pluginDescriptor: RawPluginDescriptor? = null,
)

private fun writeModuleInfo(writer: JsonGenerator, item: ModuleInfo, projectHomePath: Path) {
  writer.obj {
    writer.writeStringField("name", item.name ?: item.sourceModule.name)
    writer.writeStringField("package", item.packageName)
    writer.writeStringField("descriptor", projectHomePath.relativize(item.descriptorFile).invariantSeparatorsPathString)
    if (!item.content.isEmpty()) {
      writer.array("content") {
        for (child in item.content) {
          writeModuleInfo(writer, child, projectHomePath)
        }
      }
    }

    if (!item.dependencies.isEmpty()) {
      writer.array("dependencies") {
        writeDependencies(item.dependencies, writer)
      }
    }
  }
}

private fun writeDependencies(items: List<Reference>, writer: JsonGenerator) {
  for (entry in items) {
    writer.obj {
      writer.writeStringField(if (entry.isPlugin) "plugin" else "module", entry.name)
    }
  }
}

internal class PluginValidationError(message: String, val sourceModule: JpsModule) : RuntimeException(message)

internal fun hasContentOrDependenciesInV2Format(descriptor: RawPluginDescriptor): Boolean {
  return descriptor.contentModules.isNotEmpty() || descriptor.dependencies.isNotEmpty()
}

private val JpsModule.productionSourceRoots: Sequence<JpsModuleSourceRoot>
  get() = sourceRoots.asSequence().filter { !it.rootType.isForTests }

private val JpsModule.testSourceRoots: Sequence<JpsModuleSourceRoot>
  get() = sourceRoots.asSequence().filter { it.rootType.isForTests }

private fun JpsModuleSourceRoot.findFile(relativePath: String): Path? {
  return JpsJavaExtensionService.getInstance().findSourceFile(this, relativePath)
}