// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PathManager.getHomePath
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.junit5.NamedFailure
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.jps.model.module.JpsModule
import org.opentest4j.MultipleFailuresError
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2

data class PluginValidationOptions(
  val skipUnresolvedOptionalContentModules: Boolean = false,
  val reportDependsTagInPluginXmlWithPackageAttribute: Boolean = true,
  val referencedPluginIdsOfExternalPlugins: Set<String> = emptySet(),
)

fun validatePluginModel(projectPath: Path, validationOptions: PluginValidationOptions = PluginValidationOptions()): PluginValidationResult {
  val modules = IntelliJProjectConfiguration.loadIntelliJProject(projectPath.toString())
    .modules
    .map { ModuleWrap(it) }

  return validatePluginModel(modules, validationOptions)
}

fun validatePluginModel(sourceModules: List<PluginModelValidator.Module>, 
                        validationOptions: PluginValidationOptions = PluginValidationOptions()): PluginValidationResult {
  return PluginModelValidator(sourceModules, validationOptions).validate()
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

  fun graphAsString(): CharSequence {
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
          writeModuleInfo(writer, item)
        }
      }
    }
    return stringWriter.buffer
  }

  fun writeGraph(outFile: Path) {
    PluginGraphWriter(pluginIdToInfo).write(outFile)
  }
}

private val emptyPath by lazy {
  Path.of("/")
}

internal val homePath by lazy {
  Path.of(getHomePath())
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "SpellCheckingInspection")
private val moduleSkipList = java.util.Set.of(
  "fleet",
  "intellij.indexing.shared.ultimate.plugin.internal.generator",
  "intellij.indexing.shared.ultimate.plugin.public",
  "kotlin-ultimate.appcode-kmm.main", /* Used only when running from sources */
  "intellij.idea.ultimate.min.customization", //has the same plugin ID as intellij.idea.ultimate.customization 
  "intellij.javaFX.community",
  "intellij.vcs.gitlab.community", //has the same plugin ID as intellij.vcs.gitlab.ultimate 
  "intellij.lightEdit",
  "intellij.webstorm",
  "intellij.datagrip", //the core plugin with 'com.intellij' ID
  "intellij.gateway", //the core plugin with 'com.intellij' ID
  "intellij.cwm", /* remote-dev/cwm-plugin/resources/META-INF/plugin.xml doesn't have `id` - ignore for now */
  "intellij.osgi", /* no particular package prefix to choose */
  "intellij.hunspell", /* MP-3656 Marketplace doesn't allow uploading plugins without dependencies */
  "intellij.android.device-explorer", /* android plugin doesn't follow the new plugin model yet, $modulename$.xml is not a module descriptor */
  "intellij.bigdatatools.plugin.spark", /* Spark Scala depends on Scala, Scala is not in monorepo*/
  "kotlin.highlighting.shared",
  "intellij.platform.syntax.psi", /* syntax.psi is not yet a real module because it's a part of Core */
)

class PluginModelValidator(private val sourceModules: List<Module>, private val validationOptions: PluginValidationOptions) {
  sealed interface Module {
    val name: String

    val sourceRoots: Sequence<Path>
    
    val testSourceRoots: Sequence<Path>
  }

  private val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()

  private val _errors = mutableListOf<PluginValidationError>()

  fun validate(): PluginValidationResult {
    // 1. collect plugin and module file info set
    val sourceModuleNameToFileInfo =
      sourceModules
        .filterNot { it.name.startsWith("fleet.") || moduleSkipList.contains(it.name) }
        .mapNotNull { createFileInfo(it) }
        .associateBy { it.sourceModule.name }

    val moduleNameToInfo = HashMap<String, ModuleInfo>()

    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      checkModuleFileInfo(
        moduleDescriptorFileInfo = moduleMetaInfo,
        moduleName = sourceModuleName,
        moduleNameToInfo = moduleNameToInfo,
      )
    }

    // 2. process plugins - process content to collect modules
    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      // interested only in plugins
      val descriptor = moduleMetaInfo.pluginDescriptor ?: continue
      val descriptorFile = moduleMetaInfo.pluginDescriptorFile ?: continue

      val id = descriptor.getChild("id")?.content
               ?: descriptor.getChild("name")?.content
               // can't specify 'com.intellij', because there is an ultimate plugin with the same ID
               ?: if (sourceModuleName == "intellij.idea.community.customization") "com.intellij.community" else null
      if (id == null) {
        _errors.add(PluginValidationError(
          "Plugin id is not specified",
          moduleMetaInfo.sourceModule,
          mapOf(
            "descriptorFile" to descriptorFile
          ),
        ))
        continue
      }

      val moduleInfo = ModuleInfo(
        pluginId = id,
        name = null,
        sourceModule = moduleMetaInfo.sourceModule,
        descriptorFile = descriptorFile,
        packageName = descriptor.getAttributeValue("package"),
        descriptor = descriptor,
      )
      val prev = pluginIdToInfo.put(id, moduleInfo)
      // todo how do we can exclude it automatically
      if (prev != null && id != "com.jetbrains.ae.database" && id != "org.jetbrains.plugins.github") {
        throw PluginValidationError(
          "Duplicated plugin id: $id",
          moduleMetaInfo.sourceModule,
          mapOf(
            "prev" to prev,
            "current" to moduleInfo,
          ),
        )
      }

      descriptor.getChild("content")?.let { contentElement ->
        checkContent(
          content = contentElement,
          referencingModuleInfo = moduleInfo,
          sourceModuleNameToFileInfo = sourceModuleNameToFileInfo,
          moduleNameToInfo = moduleNameToInfo,
        )
      }
    }

    // 3. check dependencies - we are aware about all modules now
    for (pluginInfo in pluginIdToInfo.values) {
      val descriptor = pluginInfo.descriptor

      @Suppress("IdentifierGrammar")
      val dependenciesElements = descriptor.children("dependencies").toList()
      if (dependenciesElements.size > 1) {
        _errors.add(PluginValidationError(
          "The only `dependencies` tag is expected",
          pluginInfo.sourceModule,
          mapOf(
            "descriptorFile" to pluginInfo.descriptorFile,
          ),
        ))
      }
      else if (dependenciesElements.size == 1) {
        checkDependencies(dependenciesElements.first(), pluginInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo)
      }

      // in the end, after processing content and dependencies
      if (validationOptions.reportDependsTagInPluginXmlWithPackageAttribute && pluginInfo.packageName != null) {
        descriptor.children.firstOrNull {
          it.name == "depends" && it.getAttributeValue("optional") == null
        }?.let {
          _errors.add(PluginValidationError(
            "The old format should not be used for a plugin with the specified package prefix, but `depends` tag is used." +
            " Please use the new format (see https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md#the-dependencies-element)",
            pluginInfo.sourceModule,
            mapOf(
              "descriptorFile" to pluginInfo.descriptorFile,
              "depends" to it,
            ),
          ))
        }
      }

      for (contentModuleInfo in pluginInfo.content) {
        contentModuleInfo.descriptor.getChild("dependencies")?.let { dependencies ->
          checkDependencies(
            element = dependencies,
            referencingModuleInfo = contentModuleInfo,
            referencingPluginInfo = pluginInfo,
            moduleNameToInfo = moduleNameToInfo,
            sourceModuleNameToFileInfo = sourceModuleNameToFileInfo,
          )
        }

        contentModuleInfo.descriptor.getChild("depends")?.let {
          _errors.add(PluginValidationError(
            "Old format must be not used for a module but `depends` tag is used",
            pluginInfo.sourceModule,
            mapOf(
              "descriptorFile" to contentModuleInfo.descriptorFile,
              "depends" to it,
            ),
          ))
        }
      }
    }
    
    return PluginValidationResult(_errors, pluginIdToInfo)
  }

  private fun checkDependencies(element: XmlElement,
                                referencingModuleInfo: ModuleInfo,
                                referencingPluginInfo: ModuleInfo,
                                moduleNameToInfo: Map<String, ModuleInfo>,
                                sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>) {
    val moduleDependenciesCount = element.children.count { 
      it.name == "module" || it.name == "plugin" && it.getAttributeValue("id")?.startsWith("com.intellij.modules.") == true
    }
    
    for (child in element.children) {

      fun registerError(message: String, fix: String? = null) {
        _errors.add(PluginValidationError(
          message,
          referencingModuleInfo.sourceModule,
          mapOf(
            "entry" to child,
            "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
          ),
          fix
        ))
      }

      if (child.name != "module") {
        if (child.name == "plugin") {
          // todo check that the referenced plugin exists
          val id = child.getAttributeValue("id")
          if (id == null) {
            registerError("Id is not specified for dependency on plugin")
            continue
          }
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
            registerError("Do not add dependency on a parent plugin")
            continue
          }

          val dependency = pluginIdToInfo[id]
          if (!id.startsWith("com.intellij.modules.") && id !in validationOptions.referencedPluginIdsOfExternalPlugins && dependency == null) {
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

        if (referencingModuleInfo.isPlugin) {
          registerError("Unsupported dependency type: ${child.name}")
          continue
        }
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        registerError("Module name is not specified")
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        continue
      }

      if (child.attributes.size > 1) {
        registerError("Unknown attributes: ${child.attributes.entries.filter { it.key != "name" }}")
        continue
      }

      val moduleInfo = moduleNameToInfo.get(moduleName)
      if (moduleInfo == null) {
        val moduleDescriptorFileInfo = sourceModuleNameToFileInfo.get(moduleName)
        if (moduleDescriptorFileInfo != null) {
          if (moduleDescriptorFileInfo.pluginDescriptor != null) {
            registerError(
              message = "Dependency on plugin must be specified using `plugin` and not `module`",
              fix = """
                    Change dependency element to:
                    
                    <plugin id="${moduleDescriptorFileInfo.pluginDescriptor.getChild("id")?.content}"/>
                  """,
            )
            continue
          }
        }
        if (!moduleName.startsWith("kotlin.")) {
          // kotlin modules are loaded via conditional includes and the test cannot detect them
          registerError("Module not found: $moduleName")
        }
        continue
      }

      referencingModuleInfo.dependencies.add(Reference(moduleName, isPlugin = false, moduleInfo))

      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          registerError("Module, that used as dependency, must be not specified in `depends`")
          break
        }
      }
    }
  }

  // For plugin two variants:
  // 1) depends + dependency on plugin in a referenced descriptor = optional descriptor. In old format: depends tag
  // 2) no depends + no dependency on plugin in a referenced descriptor = directly injected into plugin (separate classloader is not created
  // during a transition period). In old format: xi:include (e.g. <xi:include href="dockerfile-language.xml"/>).
  private fun checkContent(content: XmlElement,
                           referencingModuleInfo: ModuleInfo,
                           sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>,
                           moduleNameToInfo: MutableMap<String, ModuleInfo>) {
    for (child in content.children) {

      fun registerError(message: String, additionalParams: Map<String, Any?> = emptyMap()) {
        _errors.add(PluginValidationError(
          message,
          referencingModuleInfo.sourceModule,
          mapOf(
            "entry" to child,
            "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
          ) + additionalParams,
        ))
      }

      if (child.name != "module") {
        registerError("Unexpected element: $child")
        continue
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        registerError("Module name is not specified",
          )
        continue
      }

      val moduleLoadingRule = child.getAttributeValue("loading")
      if (moduleLoadingRule != null && moduleLoadingRule !in arrayOf("required", "embedded", "optional", "on-demand")) {
        registerError("Unknown value for 'loading' attribute: $moduleLoadingRule. Supported values are 'required', 'embedded', 'optional' and 'on-demand'.")
        continue
      }

      if (child.attributes.size > 2) {
        registerError("Unknown attributes: ${child.attributes.entries.filter { it.key != "name" || it.key != "loading" }}")
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        registerError("intellij.platform.commercial.verifier is not supposed to be used as content of plugin")
        continue
      }

      // ignore null - getModule reports error
      val moduleDescriptorFileInfo = getModuleDescriptorFileInfo(
        moduleName = moduleName,
        moduleLoadingRule = moduleLoadingRule,
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
      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          registerError(
            "Module must be not specified in `depends`.",
            mapOf(
              "referencedDescriptorFile" to moduleInfo.descriptorFile
            ),
          )
          continue
        }
      }

      moduleDescriptor.getChild("content")?.let {
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
      packageName = moduleDescriptor.getAttributeValue("package"),
      descriptor = moduleDescriptor,
    )
    moduleNameToInfo.put(moduleName, moduleInfo)
    return moduleInfo
  }

  private fun getModuleDescriptorFileInfo(
    moduleName: String,
    moduleLoadingRule: String?,
    referencingModuleInfo: ModuleInfo,
    sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>
  ): ModuleDescriptorFileInfo? {
    var module = sourceModuleNameToFileInfo.get(moduleName.removeSuffix("._test"))
    if (module != null) {
      return module
    }

    fun registerError(message: String) {
      _errors.add(PluginValidationError(message, referencingModuleInfo.sourceModule, mapOf(
        "referencingDescriptorFile" to referencingModuleInfo.descriptorFile
      )))
    }

    val containingModuleName = moduleName.substringBefore('/')
    module = sourceModuleNameToFileInfo[containingModuleName]
    if (module == null) {
      if (moduleLoadingRule == "required" || moduleLoadingRule == "embedded" || !validationOptions.skipUnresolvedOptionalContentModules) {
        registerError("Cannot find module $containingModuleName")
      }
      return null
    }

    val fileName = "${moduleName.replace('/', '.')}.xml"
    val result = loadFileInModule(sourceModule = module.sourceModule, fileName = fileName)
    if (result == null) {
      _errors.add(PluginValidationError(
        message = "Module ${module.sourceModule.name} doesn't have descriptor file",
        sourceModule = referencingModuleInfo.sourceModule,
        params = mapOf(
          "expectedFile" to fileName,
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        ),
        fix = """
              Create file $fileName in ${pathToShortString(module.sourceModule.sourceRoots.first())}
              with content:
              
              <idea-plugin package="REPLACE_BY_MODULE_PACKAGE">
              </idea-plugin>
            """
      ))
    }
    return result
  }

  private fun createFileInfo(module: Module): ModuleDescriptorFileInfo? {
    for (sourceRoot in module.sourceRoots) {
      val metaInf = sourceRoot.resolve("META-INF")
      val moduleXml = metaInf.resolve("${module.name}.xml")
      if (Files.exists(moduleXml)) {
        _errors.add(PluginValidationError(
          "Module descriptor must be in the root of module root",
          module,
          mapOf(
            "module" to module.name,
            "moduleDescriptor" to moduleXml,
          ),
        ))
      }
    }
    
    val pluginFileName = when (module.name) {
      "intellij.platform.backend.split" -> "pluginBase.xml"
      "intellij.idea.community.customization" -> "IdeaPlugin.xml"
      else -> "plugin.xml"
    }

    val pluginDescriptors =
      module.sourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val pluginDescriptorFile: Path = sourceRoot.resolve("META-INF").resolve(pluginFileName)
        readXmlAsModelOrNull(pluginDescriptorFile)?.let { pluginDescriptorFile to it }
      }

    val moduleDescriptors =
      module.sourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val moduleDescriptorFile: Path = sourceRoot.resolve("${module.name}.xml")
        readXmlAsModelOrNull(moduleDescriptorFile)?.let { moduleDescriptorFile to it }
      }

    if (pluginDescriptors.size > 1) {
      _errors.add(PluginValidationError(
        "Duplicated plugin.xml",
        module,
        mapOf(
          "module" to module.name,
          "firstPluginDescriptor" to pluginDescriptors[0].first,
          "secondPluginDescriptor" to pluginDescriptors[1].first,
        ),
      ))
      return null
    }
    if (moduleDescriptors.size > 1) {
      _errors.add(PluginValidationError(
        "Duplicated module descriptor",
        module,
        mapOf(
           "module" to module.name,
           "firstDescriptor" to moduleDescriptors[0].first,
           "secondDescriptor" to moduleDescriptors[1].first,
        )
      ))
    }

    val testModuleDescriptors =
      module.testSourceRoots.mapNotNullTo(ArrayList()) { sourceRoot ->
        val moduleDescriptorFile: Path = sourceRoot.resolve("${module.name}._test.xml")
        readXmlAsModelOrNull(moduleDescriptorFile)?.let { moduleDescriptorFile to it }
      }

    val moduleDescriptorWithFile = (moduleDescriptors + testModuleDescriptors).firstOrNull()
    val moduleDescriptorFile = moduleDescriptorWithFile?.first
    val moduleDescriptor = moduleDescriptorWithFile?.second
    val pluginDescriptorFile = pluginDescriptors.singleOrNull()?.first
    val pluginDescriptor = pluginDescriptors.singleOrNull()?.second
    
    //todo: this is violated in some modules; maybe we should extract plugin.xml to a separate module and uncomment this.
    /*
    if (pluginDescriptorFile != null && moduleDescriptorFile != null) {
      _errors.add(PluginValidationError(
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
}

internal data class ModuleInfo(
  @JvmField val pluginId: String?,
  @JvmField val name: String?,
  @JvmField val sourceModule: PluginModelValidator.Module,
  @JvmField val descriptorFile: Path,
  @JvmField val packageName: String?,

  @JvmField val descriptor: XmlElement,
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
  @JvmField val sourceModule: PluginModelValidator.Module,

  @JvmField val moduleDescriptor: XmlElement? = null,
  @JvmField val moduleDescriptorFile: Path? = null,
  @JvmField val pluginDescriptorFile: Path? = null,
  @JvmField val pluginDescriptor: XmlElement? = null,
)

private fun writeModuleInfo(writer: JsonGenerator, item: ModuleInfo) {
  writer.obj {
    writer.writeStringField("name", item.name ?: item.sourceModule.name)
    writer.writeStringField("package", item.packageName)
    writer.writeStringField("descriptor", pathToShortString(item.descriptorFile))
    if (!item.content.isEmpty()) {
      writer.array("content") {
        for (child in item.content) {
          writeModuleInfo(writer, child)
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

internal fun pathToShortString(file: Path): String {
  return when {
    file === emptyPath -> ""
    homePath.fileSystem === file.fileSystem -> homePath.relativize(file).toString()
    else -> file.toString()
  }
}

private fun writeDependencies(items: List<Reference>, writer: JsonGenerator) {
  for (entry in items) {
    writer.obj {
      writer.writeStringField(if (entry.isPlugin) "plugin" else "module", entry.name)
    }
  }
}

internal class PluginValidationError private constructor(message: String, val sourceModule: PluginModelValidator.Module) : RuntimeException(message) {
  constructor(
    message: String,
    sourceModule: PluginModelValidator.Module,
    params: Map<String, Any?> = mapOf(),
    fix: String? = null,
  ) : this(
    params.entries.joinToString(
      prefix = "$message (\n  ",
      separator = ",\n  ",
      postfix = "\n)" + (fix?.let { "\n\nProposed fix:\n\n" + fix.trimIndent() + "\n\n" } ?: "")
    ) {
      it.key + "=" + paramValueToString(it.value)
    },
    sourceModule
  )
}

private fun paramValueToString(value: Any?): String {
  return when (value) {
    is Path -> pathToShortString(value)
    else -> value.toString()
  }
}

private fun loadFileInModule(sourceModule: PluginModelValidator.Module, fileName: String): ModuleDescriptorFileInfo? {
  for (sourceRoot in sourceModule.sourceRoots) {
    val moduleDescriptorFile = sourceRoot.resolve(fileName)
    val moduleDescriptor = readXmlAsModelOrNull(moduleDescriptorFile) ?: continue
    return ModuleDescriptorFileInfo(
      sourceModule = sourceModule,
      moduleDescriptor = moduleDescriptor,
      moduleDescriptorFile = moduleDescriptorFile,
    )
  }
  return null
}

private const val COMMON_IDE = "/META-INF/common-ide-modules.xml"

private fun readXmlAsModelOrNull(file: Path): XmlElement? {
  try {
    val element = Files.newInputStream(file).use(::readXmlAsModel)

    val xInclude = element.children("include").firstOrNull { it.getAttributeValue("href") == COMMON_IDE }
    if (xInclude != null) {
      val commonFile = Path.of(PathManager.getCommunityHomePath(), "platform/platform-resources/src/$COMMON_IDE")
      return mergeContent(main = element, Files.newInputStream(commonFile).use(::readXmlAsModel))
    }
    return element
  }
  catch (_: NoSuchFileException) {
    return null
  }
}

private fun mergeContent(main: XmlElement, contentHolder: XmlElement): XmlElement {
  return XmlElement(
    name = main.name,
    attributes = main.attributes,
    content = main.content,
    children = main.children.map { child ->
      if (child.name == "content") {
        XmlElement(
          name = child.name,
          attributes = child.attributes,
          children = child.children + contentHolder.children("content").flatMap { it.children },
        )
      }
      else {
        child
      }
    },
  )
}

internal fun hasContentOrDependenciesInV2Format(descriptor: XmlElement): Boolean {
  return descriptor.children.any { it.name == "content" || it.name == "dependencies" }
}

private data class ModuleWrap(private val module: JpsModule) : PluginModelValidator.Module {
  override val name: String
    get() = module.name

  override val sourceRoots: Sequence<Path>
    get() {
      return module.sourceRoots
        .asSequence()
        .filter { !it.rootType.isForTests }
        .map { it.path }
    }

  override val testSourceRoots: Sequence<Path>
    get() {
      return module.sourceRoots
        .asSequence()
        .filter { it.rootType.isForTests }
        .map { it.path }
    }
}