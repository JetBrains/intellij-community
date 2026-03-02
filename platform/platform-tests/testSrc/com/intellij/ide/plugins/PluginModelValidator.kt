// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.RawPluginDescriptor
import com.intellij.platform.pluginSystem.parser.impl.ScopedElementsContainer
import com.intellij.platform.pluginSystem.parser.impl.elements.ContentModuleElement
import com.intellij.platform.pluginSystem.parser.impl.elements.DependenciesElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ServiceElement
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.junit5.NamedFailure
import com.intellij.testFramework.junit5.groupFailures
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.createGenerator
import com.intellij.util.io.jackson.obj
import com.intellij.util.io.jackson.writeFieldName
import com.intellij.util.io.jackson.writeStringField
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import tools.jackson.core.JsonGenerator
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultPrettyPrinter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

data class CorePluginDescription(
  val mainModuleName: String,
  val rootPluginXmlName: String = "plugin.xml",
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
  val skipServicesOverridesCheck: Boolean = false,
  val reportDependsTagInPluginXmlWithPackageAttribute: Boolean = true,
  val referencedPluginIdsOfExternalPlugins: Set<String> = emptySet(),

  val pluginModelBuilderOptions: SimplifiedPluginModelBuilderOptions = SimplifiedPluginModelBuilderOptions(),

  /**
   * Set of modules containing `plugin.xml` files which should be ignored because they correspond to smaller editions of plugins,
   * and other module contains `plugin.xml` file with the same ID. 
   * It's better to avoid such configurations, and include all optional parts as content modules in a single `plugin.xml`. 
   */
  val mainModulesOfAlternativePluginVariants: Set<String> = emptySet(),

  /**
   * Mapping from a plugin ID to the list of its content modules which don't have a dedicated JPS module and registered using deprecated
   * `module.name/subDescriptor` syntax.
   */
  val pluginsToContentModulesWithoutDedicatedJpsModules: Map<String, List<String>> = emptyMap(),

  /**
   * Set of implementation classes of existing application-level and project-level components which shouldn't be reported as errors. 
   */
  val componentImplementationClassesToIgnore: Set<String> = emptySet(),

  /**
   * Names of service interfaces that are overridden by plugins which sources are located outside the current project, and therefore need
   * to be registered as `open`.
   */
  val externallyOverriddenServices: Set<String> = emptySet(),
)

fun validatePluginModel(projectPath: Path, validationOptions: PluginValidationOptions = PluginValidationOptions()): PluginValidationResult {
  val project = IntelliJProjectConfiguration.loadIntelliJProject(projectPath.toString())
  return validatePluginModel(project, projectPath, validationOptions)
}

/**
 * Runs [PluginModelValidator] on the specified [project] and returns the result.
 */
fun validatePluginModel(
  project: JpsProject, projectHomePath: Path,
  validationOptions: PluginValidationOptions = PluginValidationOptions(),
): PluginValidationResult {
  val builder = SimplifiedPluginModelBuilder(project, validationOptions.pluginModelBuilderOptions)
  return PluginModelValidator(builder.buildSimplifiedPluginModel(),
                              projectHomePath = projectHomePath,
                              validationOptions = validationOptions).validate()
}

class PluginValidationResult internal constructor(
  private val validationErrors: List<PluginValidationError>,
  private val pluginIdToInfo: Map<String, ModuleInfo>,
) {
  val errors: List<Throwable>
    get() = java.util.List.copyOf(validationErrors)

  val namedFailures: List<NamedFailure>
    get() {
      return validationErrors.groupFailures { it.sourceModule.name }
    }

  fun graphAsString(projectHomePath: Path): CharSequence {
    val stringWriter = StringWriter()
    val writer = JsonFactory().createGenerator(stringWriter, DefaultPrettyPrinter())
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
}

/**
 * Performs checks of plugin and module descriptors in the source code of [project].
 * The checks don't depend on the layout of plugins and don't require all modules to be compiled.
 * There is [com.intellij.platform.buildScripts.testFramework.pluginModel.PluginDependenciesValidator] which checks dependencies of plugin
 * modules.
 */
internal class PluginModelValidator(
  private val simplifiedPluginModel: SimplifiedPluginModel,
  private val projectHomePath: Path,
  private val validationOptions: PluginValidationOptions,
) {
  private val pluginIdToInfo = simplifiedPluginModel.pluginIdToInfo
  private val pluginAliases = simplifiedPluginModel.pluginAliases
  private val _errors = mutableListOf<PluginValidationError>()

  init {
    simplifiedPluginModel.errors.forEach { reportError(it.message, it.sourceModule, it.params) }
  }

  fun validate(): PluginValidationResult {
    val descriptorFileInfos = simplifiedPluginModel.descriptorFileInfos
    val moduleNameToInfo = simplifiedPluginModel.moduleNameToInfo
    val allMainModulesOfPlugins = simplifiedPluginModel.allMainModulesOfPlugins

    val contentModuleNameToFileInfo = descriptorFileInfos.filterIsInstance<ContentModuleDescriptorFileInfo>().associateBy { it.contentModuleName }
    val sourceModuleNameToPluginFileInfo = descriptorFileInfos.filterIsInstance<PluginDescriptorFileInfo>().associateBy { it.sourceModule.name }
    for (pluginInfo in allMainModulesOfPlugins) {
      checkPluginMainDescriptor(pluginInfo.descriptor, pluginInfo.sourceModule, pluginInfo)
      checkContent(
        contentElements = pluginInfo.descriptor.contentModules,
        referencingModuleInfo = pluginInfo,
        contentModuleNameToFileInfo = contentModuleNameToFileInfo,
        moduleNameToInfo = moduleNameToInfo,
      )
      checkModuleElements(moduleDescriptor = pluginInfo.descriptor, sourceModule = pluginInfo.sourceModule, pluginInfo.descriptorFile)
    }

    // additional content check: services overrides
    if (!validationOptions.skipServicesOverridesCheck) {
      checkServicesOverrides(descriptorFileInfos, RawPluginDescriptor::projectElementsContainer)
      checkServicesOverrides(descriptorFileInfos, RawPluginDescriptor::appElementsContainer)
    }

    // 3. check dependencies - we are aware about all modules now
    val contentModuleToContainingPlugins = HashMap<String, MutableList<ModuleInfo>>()
    for (pluginInfo in allMainModulesOfPlugins) {
      pluginInfo.content.groupByTo(contentModuleToContainingPlugins, { it.name!! }, { pluginInfo })
    }

    for (pluginInfo in allMainModulesOfPlugins) {
      val descriptor = pluginInfo.descriptor

      for (incompatibleWithId in descriptor.incompatibleWith) {
        if (incompatibleWithId !in pluginIdToInfo && incompatibleWithId !in pluginAliases
            && incompatibleWithId !in validationOptions.referencedPluginIdsOfExternalPlugins) {
          reportError("'incompatible-with' refers to unknown plugin '$incompatibleWithId'", pluginInfo.sourceModule,
                      mapOf("descriptorFile" to pluginInfo.descriptorFile))
        }
      }

      val moduleNameToLoadingRule = pluginInfo.descriptor.contentModules
        .associateBy({ it.name }, { it.loadingRule })
      checkDependencies(
        dependenciesElements = descriptor.dependencies,
        referencingModuleInfo = pluginInfo,
        referencingPluginInfo = pluginInfo,
        moduleNameToInfo = moduleNameToInfo,
        sourceModuleNameToPluginFileInfo = sourceModuleNameToPluginFileInfo,
        contentModuleToContainingPlugins = contentModuleToContainingPlugins,
        isMainModule = true,
        contentModuleNameFromThisPluginToLoadingRule = moduleNameToLoadingRule,
      )

      val duplicateContentModules = pluginInfo.content.groupBy { it.name }.filter { it.value.size > 1 }.keys
      if (duplicateContentModules.isNotEmpty()) {
        reportError(
          "Plugin '${pluginInfo.pluginId}' has duplicated content modules declarations: ${duplicateContentModules.joinToString()}",
          pluginInfo.sourceModule,
          mapOf(
            "descriptorFile" to pluginInfo.descriptorFile,
            "duplicateContentModules" to duplicateContentModules.joinToString(),
          ),
        )
      }

      for (contentModuleInfo in pluginInfo.content) {
        checkDependencies(
          dependenciesElements = contentModuleInfo.descriptor.dependencies,
          referencingModuleInfo = contentModuleInfo,
          referencingPluginInfo = pluginInfo,
          moduleNameToInfo = moduleNameToInfo,
          sourceModuleNameToPluginFileInfo = sourceModuleNameToPluginFileInfo,
          contentModuleToContainingPlugins = contentModuleToContainingPlugins,
          isMainModule = false,
          contentModuleNameFromThisPluginToLoadingRule = moduleNameToLoadingRule,
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

      // in the end, after processing content and dependencies
      checkDepends(pluginInfo, descriptor)
    }

    return PluginValidationResult(_errors, pluginIdToInfo)
  }

  private fun getOpenServices(
    services: List<ServiceElement>,
    descriptor: DescriptorFileInfo,
  ): Set<String> {
    return services
      .filter { it.open }
      .mapNotNull { getServiceInterface(it, descriptor) }
      .toSet()
  }

  private fun getOverriddenServices(
    services: List<ServiceElement>,
    descriptor: DescriptorFileInfo,
  ): Set<String> {
    return services
      .filter { it.overrides }
      .mapNotNull { getServiceInterface(it, descriptor) }
      .toSet()
  }

  private fun getServiceInterface(service: ServiceElement, descriptorForLogging: DescriptorFileInfo): String? {
    val serviceInterface = service.serviceInterface ?: service.serviceImplementation
    if (serviceInterface == null) {
      reportError("Services declared must declare `serviceInterfance` or `serviceImplementation`\n" +
                  "$service in ${descriptorForLogging.descriptorFile}", descriptorForLogging.sourceModule)
    }
    return serviceInterface
  }

  private fun checkServicesOverrides(descriptors: Collection<DescriptorFileInfo>, containerSelector: (RawPluginDescriptor) -> ScopedElementsContainer) {
    val allOpenServices = descriptors.flatMapTo(HashSet()) {
      getOpenServices(containerSelector(it.descriptor).services, it)
    }
    val allOverriddenServices = descriptors.flatMapTo(HashSet()) {
      getOverriddenServices(containerSelector(it.descriptor).services, it)
    }

    for (descriptor in descriptors) {
      checkServicesOverridesInSingleScopedContainer(descriptor,
                                                    containerSelector(descriptor.descriptor).services,
                                                    allOpenServices,
                                                    allOverriddenServices)
    }
  }

  private fun checkServicesOverridesInSingleScopedContainer(
    descriptor: DescriptorFileInfo,
    servicesInContainerAndInDescriptor: List<ServiceElement>,
    allOpenServicesInContainer: Set<String>,
    allOverriddenServicesInContainer: Set<String>,
  ) {
    getOverriddenServices(servicesInContainerAndInDescriptor, descriptor)
      .filterNot { allOpenServicesInContainer.contains(it) }
      .forEach { serviceInterface ->
        reportError("Service $serviceInterface is not open for override.\n" +
                    "Please either add `open='true'` to the service declaration you want to override, or remove `overrides='true'` in ${descriptor.descriptorFile}",
                    descriptor.sourceModule)
      }

    getOpenServices(servicesInContainerAndInDescriptor, descriptor)
      .filterNot { allOverriddenServicesInContainer.contains(it) || validationOptions.externallyOverriddenServices.contains(it) }
      .forEach { serviceInterface ->
        reportError("Service $serviceInterface is declared as open in ${descriptor.descriptorFile}, but is not overridden anywhere.\n" +
                    "Please consider making the service non-open, or add this service to `externallyOverriddenServices` if some external override exists.",
                    descriptor.sourceModule)
      }

    servicesInContainerAndInDescriptor
      .filter { !it.overrides && !it.open }
      .mapNotNull { getServiceInterface(it, descriptor) }
      .filter { allOpenServicesInContainer.contains(it) }
      .forEach { serviceInterface ->
        reportError("Service $serviceInterface is declared as open in some plugins, but not in ${descriptor.descriptorFile}.\n" +
                    "Please add `open='true'` to the your service declaration.",
                    descriptor.sourceModule)
      }
  }

  private fun checkDepends(
    pluginInfo: ModuleInfo,
    descriptor: RawPluginDescriptor,
  ) {
    val dependsPerTarget = descriptor.depends.groupBy { it.pluginId }
    for ((target, depends) in dependsPerTarget.filter { (_, depends) -> depends.any { it.isOptional } && depends.any { !it.isOptional } }) {
      if (pluginInfo.sourceModule.name in setOf("intellij.android.plugin.descriptor", "intellij.rider.plugins.android")) continue
      reportError(
        message = "Both optional and strict <depends> found targeting the plugin '${target}'.",
        sourceModule = pluginInfo.sourceModule,
        params = mapOf(
          "descriptorFile" to pluginInfo.descriptorFile,
          "depends" to depends,
        )
      )
    }

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
  }

  private fun checkDependencies(
    dependenciesElements: List<DependenciesElement>,
    referencingModuleInfo: ModuleInfo,
    referencingPluginInfo: ModuleInfo,
    moduleNameToInfo: Map<String, ModuleInfo>,
    sourceModuleNameToPluginFileInfo: Map<String, PluginDescriptorFileInfo>,
    contentModuleToContainingPlugins: HashMap<String, MutableList<ModuleInfo>>,
    isMainModule: Boolean,
    contentModuleNameFromThisPluginToLoadingRule: Map<String, ModuleLoadingRuleValue>,
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
              && id !in pluginAliases
              && IdeaPluginOsRequirement.fromModuleId(PluginId.getId(id)) == null) {
            registerError("Plugin not found: $id")
            continue
          }

          val ref = Reference(
            name = id,
            isPlugin = true,
            moduleInfo = dependency
          )
          if (referencingModuleInfo.dependencies.contains(ref)) {
            registerError("Dependency on '$id' is already declared in ${referencingModuleInfo.descriptorFile.name}", fix = "Remove duplicating dependency on '$id'")
            continue
          }
          referencingModuleInfo.dependencies.add(ref)
          continue
        }
        is DependenciesElement.ModuleDependency -> {
          val moduleName = child.moduleName
          val moduleInfo = moduleNameToInfo.get(moduleName)
          if (moduleInfo == null) {
            val moduleDescriptorFileInfo = sourceModuleNameToPluginFileInfo.get(moduleName)
            if (moduleDescriptorFileInfo != null) {
              registerError(
                message = "Dependency on plugin must be specified using `plugin` and not `module`",
                fix = """
                      Change dependency element to:
                      
                      <plugin id="${moduleDescriptorFileInfo.descriptor.id}"/>
                    """,
              )
              continue
            }
            registerError("Module not found: $moduleName")
            continue
          }

          val containingPlugins = contentModuleToContainingPlugins[moduleName]
          if (containingPlugins == null || containingPlugins.isEmpty()) {
            registerError("""
              |Module '$moduleName' is not registered as a content module, but used as a dependency.
              |Either convert it to a content module, or use dependency on the plugin which includes it instead.
              |""".trimMargin())
            continue
          }
          val loadingRule = contentModuleNameFromThisPluginToLoadingRule[moduleName]
          when {
            isMainModule && loadingRule != null -> {
              registerError("""
                        |The main module of plugin '${referencingPluginInfo.pluginId}' declares dependency on a content module '$moduleName' registered 
                        |in the same plugin. Such dependencies aren't allowed.
                        |To fix the problem, extract relevant code to a separate content module and move the dependency to it.
                        |""".trimMargin())
              continue
            }
            !isMainModule && loadingRule == ModuleLoadingRuleValue.OPTIONAL
            && moduleName != "intellij.platform.backend" -> { // remove this check when IJPL-201428 is fixed

              val thisModuleName = referencingModuleInfo.name ?: error("Module name is not specified for $referencingModuleInfo")
              val thisLoadingRule = contentModuleNameFromThisPluginToLoadingRule.getValue(thisModuleName)
              val problemDescription = when (thisLoadingRule) {
                ModuleLoadingRuleValue.EMBEDDED ->
                  "Since optional modules have implicit dependencies on the main module, this creates a circular dependency and the plugin won't load."
                ModuleLoadingRuleValue.REQUIRED ->
                  "This actually makes '${moduleName}' required as well (the plugin won't load if it's not available)."
                else -> null
              }
              if (problemDescription != null) {
                registerError("""
                    |The content module '$thisModuleName' is registered as '${thisLoadingRule.name.lowercase()}', but it depends on the module '$moduleName' which is declared as optional
                    |in the same plugin '${referencingPluginInfo.pluginId}'.
                    |$problemDescription
                    |To fix the problem, you can do one of the following:
                    | * set 'loading="required"' for '$moduleName',
                    | * set 'loading="optional"' for '$thisModuleName',
                    | * remove the dependency on '$moduleName' from '${referencingModuleInfo.descriptorFile.name}', if it's not needed.
                    |""".trimMargin())
              }
            }
          }

          referencingModuleInfo.dependencies.add(Reference(moduleName, isPlugin = false, moduleInfo))
          if (!pluginModuleVisibilityCheckDisabled) {
            when (moduleInfo.descriptor.moduleVisibility) {
              ModuleVisibilityValue.PRIVATE -> {
                if (containingPlugins.all { it.pluginId != referencingPluginInfo.pluginId }) {
                  val differentContainingPlugin = containingPlugins.first()
                  registerError("""
                  |Module '$moduleName' has 'private' (default) visibility in '${differentContainingPlugin.pluginId}' but it is used as a dependency in 
                  |a plugin '${referencingPluginInfo.pluginId}'.
                  |Use 'internal' or 'public' visibility instead by adding 'visibility' attribute to the root tag of $moduleName.xml.
                  |""".trimMargin())
                }
              }
              ModuleVisibilityValue.INTERNAL -> {
                val referencingNamespace = referencingPluginInfo.descriptor.namespace
                val containingPluginFromAnotherNamespace = containingPlugins.find { it.descriptor.namespace != referencingNamespace }
                if (containingPluginFromAnotherNamespace != null) {
                  val declaringNamespace = containingPluginFromAnotherNamespace.descriptor.namespace
                  val declaringNamespaceText =
                    if (declaringNamespace != null) "with namespace '$declaringNamespace'"
                    else "without namespace"
                  val referencingNamespaceText =
                    if (referencingNamespace != null) "from another namespace '$referencingNamespace'"
                    else "without namespace"
                  val setNamespaceFixText = when {
                    declaringNamespace == null && referencingNamespace != null -> " or set the namespace to '$referencingNamespace' in '${containingPluginFromAnotherNamespace.pluginId}' plugin"
                    declaringNamespace != null && referencingNamespace == null -> " or set the namespace to '$declaringNamespace' in '${referencingPluginInfo.pluginId}' plugin"
                    else -> " or set the same namespace in both ${containingPluginFromAnotherNamespace.pluginId} and '${referencingPluginInfo.pluginId}' plugins"
                  }
                  registerError("""
                  |Module '$moduleName' has 'internal' visibility in '${containingPluginFromAnotherNamespace.pluginId}' $declaringNamespaceText but it is used as a dependency in 
                  |a plugin '${referencingPluginInfo.pluginId}' $referencingNamespaceText.
                  |Use 'public' visibility in '$moduleName.xml'$setNamespaceFixText
                """.trimMargin())
                }
              }
              ModuleVisibilityValue.PUBLIC -> {}
            }
          }

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
    contentElements: List<ContentModuleElement>,
    referencingModuleInfo: ModuleInfo,
    contentModuleNameToFileInfo: Map<String, ContentModuleDescriptorFileInfo>,
    moduleNameToInfo: MutableMap<String, ModuleInfo>,
  ) {
    val nonPrivateModules = ArrayList<String>()
    for (contentElement in contentElements) {
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

      val moduleDescriptorFileInfo = contentModuleNameToFileInfo[moduleName]
      if (moduleDescriptorFileInfo == null) {
        if (contentElement.loadingRule == ModuleLoadingRuleValue.REQUIRED || contentElement.loadingRule == ModuleLoadingRuleValue.EMBEDDED || !validationOptions.skipUnresolvedOptionalContentModules) {
          reportError("Cannot find module $moduleName", referencingModuleInfo.sourceModule, mapOf(
            "referencingDescriptorFile" to referencingModuleInfo.descriptorFile
          ))
        }
        continue
      }

      if (moduleName.contains("/")) {
        val knownViolations = validationOptions.pluginsToContentModulesWithoutDedicatedJpsModules[referencingModuleInfo.pluginId] ?: emptyList()
        if (moduleName !in knownViolations) {
          reportError(
            message = """
              |Module '$moduleName' is registered in '${referencingModuleInfo.pluginId}' plugin using deprecated module.name/subDescriptor syntax.
              |Extract it to a separate JPS module using the quick-fix provided by the DevKit plugin as described in https://youtrack.jetbrains.com/issue/IJPL-165543.
            """.trimMargin(),
            sourceModule = moduleDescriptorFileInfo.sourceModule,
            params = mapOf(
              "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
            )
          )
        }
      }

      val moduleDescriptor = moduleDescriptorFileInfo.descriptor
      if (moduleDescriptor.moduleVisibility != ModuleVisibilityValue.PRIVATE) {
        nonPrivateModules.add(moduleName)
      }
      val moduleInfo = SimplifiedPluginModelBuilder.createModuleFileInfo(moduleDescriptorFileInfo, moduleName, moduleNameToInfo)
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

      checkContentModuleUnexpectedElements(moduleDescriptor, referencingModuleInfo.sourceModule, moduleInfo)
      checkModuleElements(moduleDescriptor, moduleInfo.sourceModule, moduleInfo.descriptorFile)

      if (moduleDescriptor.moduleVisibility != ModuleVisibilityValue.PUBLIC && moduleDescriptor.pluginAliases.isNotEmpty()) {
        val aliases =
          if (moduleDescriptor.pluginAliases.size > 1) "${moduleDescriptor.pluginAliases.size} plugin aliases (${moduleDescriptor.pluginAliases.joinToString()})"
          else "a plugin alias '${moduleDescriptor.pluginAliases.first()}'"
        registerError("""
          |Module '$moduleName' has '${moduleDescriptor.moduleVisibility.name.lowercase()}' visibility but it declares $aliases so
          |actually any module may depend on it using <dependencies><plugin> or <depends> tag.
          |If this is intended, change visibility of '$moduleName' to 'public'.
        """.trimMargin())
      }
    }

    if (nonPrivateModules.isNotEmpty() && referencingModuleInfo.descriptor.namespace == null) {
      reportError("""
        |Namespace is required for plugins with non-private content modules. 
        |However, plugin '${referencingModuleInfo.pluginId}' has ${if (nonPrivateModules.size > 1) "${nonPrivateModules.size} non-private modules" else "a non-private module '${nonPrivateModules.single()}'"},
        |but doesn't specify 'namespace' attribute in 'content' tag.
        """.trimMargin(),
                  referencingModuleInfo.sourceModule,
                  mapOf(
                    "referencedDescriptorFile" to referencingModuleInfo.descriptorFile,
                    "nonPrivateModules" to nonPrivateModules.joinToString(),
                  ))
    }
  }

  /**
   * Checks elements in the main module or a content module
   */
  private fun checkModuleElements(moduleDescriptor: RawPluginDescriptor, sourceModule: JpsModule, descriptorFile: Path) {
    fun reportError(message: String) {
      reportError(
        message = message,
        sourceModule = sourceModule,
        mapOf(
          "descriptorFile" to descriptorFile,
        ),
      )
    }

    for (extensionPointElement in moduleDescriptor.moduleElementsContainer.extensionPoints) {
      reportError("""
                    |Module-level extension point '$extensionPointElement' is defined in '${sourceModule.name}'.  
                    |Module-level extension points are deprecated in general and forbidden in intellij monorepo.
                    |Use application-level or project-level extension point, and pass 'Module' instance as a parameter if needed.
                    |""".trimMargin())
    }
    for (componentElement in moduleDescriptor.moduleElementsContainer.components) {
      reportError("""
          |Module-level component '$componentElement' is defined in '${sourceModule.name}'.
          |Module-level components are deprecated in general and forbidden in intellij monorepo.
          |Use application-level or project-level services instead, and pass 'Module' instance as a parameter if needed.
        """.trimMargin()
      )
    }
    for (listenerElement in moduleDescriptor.moduleElementsContainer.listeners) {
      reportError("Module-level listener '$listenerElement' is defined in '${sourceModule.name}', but they aren't supported.")
    }
    for (componentElement in moduleDescriptor.projectElementsContainer.components) {
      if (componentElement.implementationClass !in validationOptions.componentImplementationClassesToIgnore) {
        reportError("""
        |Project-level component '$componentElement' is defined in '${sourceModule.name}'.
        |Project-level components are deprecated in general and it's forbidden to add new ones in intellij monorepo.
        |Migrate it as described at https://plugins.jetbrains.com/docs/intellij/plugin-components.html.
      """.trimMargin())
      }
    }
    for (componentElement in moduleDescriptor.appElementsContainer.components) {
      if (componentElement.implementationClass !in validationOptions.componentImplementationClassesToIgnore) {
        reportError("""
          |Application-level component '$componentElement' is defined in '${sourceModule.name}'.
          |Application-level components are deprecated in general and it's forbidden to add new ones in intellij monorepo.
          |Migrate it as described at https://plugins.jetbrains.com/docs/intellij/plugin-components.html.
        """.trimMargin())
      }
    }
  }

  // TODO same for depends
  private fun checkContentModuleUnexpectedElements(
    moduleDescriptor: RawPluginDescriptor,
    sourceModule: JpsModule,
    moduleInfo: ModuleInfo,
  ) {
    ContentModuleDescriptor.reportContentModuleUnexpectedElements(moduleDescriptor) {
      reportError(
        "Element '$it' has no effect in a content module descriptor",
        sourceModule,
        mapOf(
          "referencedDescriptorFile" to moduleInfo.descriptorFile
        )
      )
    }
  }

  private val namespaceRegex = Regex("^[a-zA-Z0-9]+([_-][a-zA-Z0-9]+)*$")
  private val namespaceAssociatedWithJetBrainsVendor = "jetbrains"

  private fun checkPluginMainDescriptor(
    pluginDescriptor: RawPluginDescriptor,
    sourceModule: JpsModule,
    moduleInfo: ModuleInfo,
  ) {
    reportMainDescriptorUnexpectedElements(pluginDescriptor) {
      reportError(
        "Element '$it' has no effect in a plugin main descriptor",
        sourceModule,
        mapOf(
          "referencedDescriptorFile" to moduleInfo.descriptorFile
        )
      )
    }
    val namespace = pluginDescriptor.namespace
    if (namespace != null) {
      when {
        pluginDescriptor.vendor == "JetBrains" && namespace != namespaceAssociatedWithJetBrainsVendor -> {
          reportError("""
                       |Plugin '${pluginDescriptor.id}' has JetBrains as vendor, but specifies namespace '$namespace' for its content modules which isn't associated with JetBrains at the Marketplace.
                       |Use namespace="$namespaceAssociatedWithJetBrainsVendor" for JetBrains plugins.
                       """.trimMargin(),
                      sourceModule,
                      mapOf(
                        "referencedDescriptorFile" to moduleInfo.descriptorFile
                      )
          )
        }
        !namespaceRegex.matches(namespace) || namespace.length !in 5..30 -> {
          reportError(
            "Invalid namespace format: '$namespace'. Namespace must start with a letter or number and can contain letters, numbers, underscores, or hyphens, and must be between 5 and 30 characters long.",
            sourceModule,
            mapOf(
              "referencedDescriptorFile" to moduleInfo.descriptorFile
            )
          )
        }
      }
    }
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
}

data class ModuleInfo(
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

data class Reference(@JvmField val name: String, @JvmField val isPlugin: Boolean, @JvmField val moduleInfo: ModuleInfo?)

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

private val pluginModuleVisibilityCheckDisabled by lazy {
  System.getProperty("intellij.platform.plugin.modules.check.visibility") == "disabled"
}

internal class PluginValidationError(message: String, val sourceModule: JpsModule) : RuntimeException(message)

internal fun hasContentOrDependenciesInV2Format(descriptor: RawPluginDescriptor): Boolean {
  return descriptor.contentModules.isNotEmpty() || descriptor.dependencies.isNotEmpty()
}
