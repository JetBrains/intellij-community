// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PathManager.getHomePath
import com.intellij.util.getErrorsAsString
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*

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
  "intellij.javaFX.community",
  "intellij.lightEdit",
  "intellij.webstorm",
  "intellij.cwm", /* remote-dev/cwm-plugin/resources/META-INF/plugin.xml doesn't have `id` - ignore for now */
  "intellij.osgi", /* no particular package prefix to choose */
  "intellij.hunspell", /* MP-3656 Marketplace doesn't allow uploading plugins without dependencies */
  "intellij.android.device-explorer", /* android plugin doesn't follow new plugin model yet, $modulename$.xml is not a module descriptor */
  "intellij.bigdatatools.plugin.spark", /* Spark Scala depends on Scala, Scala is not in monorepo*/
  "kotlin.highlighting.shared",
)

class PluginModelValidator(sourceModules: List<Module>) {
  sealed interface Module {
    val name: String

    val sourceRoots: Sequence<Path>
  }

  private val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()

  private val _errors = mutableListOf<Throwable>()

  val errors: List<Throwable>
    get() = java.util.List.copyOf(_errors)

  val errorsAsString: CharSequence
    get() = if (_errors.isEmpty()) "" else getErrorsAsString(_errors, includeStackTrace = false)

  init {
    // 1. collect plugin and module file info set
    val sourceModuleNameToFileInfo = sourceModules.associate {
      it.name to ModuleDescriptorFileInfo(it)
    }

    for (module in sourceModules) {
      val moduleName = module.name
      if (moduleName.startsWith("fleet.") || moduleSkipList.contains(moduleName)) {
        continue
      }

      for (sourceRoot in module.sourceRoots) {
        updateFileInfo(
          moduleName = moduleName,
          sourceRoot = sourceRoot,
          fileInfo = sourceModuleNameToFileInfo.get(moduleName)!!
        )
      }
    }

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
               // can't specify 'com.intellij', because there is ultimate plugin with the same ID
               ?: if (sourceModuleName == "intellij.idea.community.customization") "com.intellij.community" else null
      if (id == null) {
        _errors.add(PluginValidationError(
          "Plugin id is not specified",
          mapOf(
            "descriptorFile" to descriptorFile
          ),
        ))
        continue
      }

      val moduleInfo = ModuleInfo(
        pluginId = id,
        name = null,
        sourceModuleName = sourceModuleName,
        descriptorFile = descriptorFile,
        packageName = descriptor.getAttributeValue("package"),
        descriptor = descriptor,
      )
      val prev = pluginIdToInfo.put(id, moduleInfo)
      // todo how do we can exclude it automatically
      if (prev != null && id != "com.jetbrains.ae.database" && id != "org.jetbrains.plugins.github") {
        throw PluginValidationError(
          "Duplicated plugin id: $id",
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
          mapOf(
            "descriptorFile" to pluginInfo.descriptorFile,
          ),
        ))
      }
      else if (dependenciesElements.size == 1) {
        checkDependencies(dependenciesElements.first(), pluginInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo)
      }

      // in the end, after processing content and dependencies
      if (pluginInfo.packageName != null) {
        descriptor.children.firstOrNull {
          it.name == "depends" && it.getAttributeValue("optional") == null
        }?.let {
          _errors.add(PluginValidationError(
            "The old format should not be used for a plugin with the specified package prefix, but `depends` tag is used." +
            " Please use the new format (see https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md#the-dependencies-element)",
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
            mapOf(
              "descriptorFile" to contentModuleInfo.descriptorFile,
              "depends" to it,
            ),
          ))
        }
      }
    }
  }

  fun graphAsString(): CharSequence {
    val stringWriter = StringWriter()
    val writer = JsonFactory().createGenerator(stringWriter)
    writer.useDefaultPrettyPrinter()
    writer.use {
      writer.obj {
        val entries = pluginIdToInfo.entries.toMutableList()
        entries.sortBy { it.value.sourceModuleName }
        for (entry in entries) {
          val item = entry.value
          if (item.packageName == null && !hasContentOrDependenciesInV2Format(item.descriptor)) {
            continue
          }

          writer.writeFieldName(item.sourceModuleName)
          writeModuleInfo(writer, item)
        }
      }
    }
    return stringWriter.buffer
  }

  fun writeGraph(outFile: Path) {
    PluginGraphWriter(pluginIdToInfo).write(outFile)
  }

  private fun checkDependencies(element: XmlElement,
                                referencingModuleInfo: ModuleInfo,
                                referencingPluginInfo: ModuleInfo,
                                moduleNameToInfo: Map<String, ModuleInfo>,
                                sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>) {
    for (child in element.children) {
      fun getErrorInfo(): Map<String, Any?> {
        return mapOf(
          "entry" to child,
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        )
      }

      if (child.name != "module") {
        if (child.name == "plugin") {
          // todo check that the referenced plugin exists
          val id = child.getAttributeValue("id")
          if (id == null) {
            _errors.add(PluginValidationError(
              "Id is not specified for dependency on plugin",
              getErrorInfo(),
            ))
            continue
          }
          if (id == "com.intellij.modules.java") {
            _errors.add(PluginValidationError(
              "Use com.intellij.java id instead of com.intellij.modules.java",
              getErrorInfo(),
            ))
            continue
          }
          if (id == "com.intellij.modules.platform") {
            _errors.add(PluginValidationError(
              "No need to specify dependency on $id",
              getErrorInfo(),
            ))
            continue
          }
          if (id == referencingPluginInfo.pluginId) {
            _errors.add(PluginValidationError(
              "Do not add dependency on a parent plugin",
              getErrorInfo(),
            ))
            continue
          }

          val dependency = pluginIdToInfo[id]
          if (!id.startsWith("com.intellij.modules.") && dependency == null) {
            _errors.add(PluginValidationError(
              "Plugin not found: $id",
              getErrorInfo(),
            ))
            continue
          }

          val ref = Reference(
            name = id,
            isPlugin = true,
            moduleInfo = dependency ?: ModuleInfo(null, id, "", emptyPath, null,
                                                  XmlElement("", Collections.emptyMap(), Collections.emptyList(), null))
          )
          if (referencingModuleInfo.dependencies.contains(ref)) {
            _errors.add(PluginValidationError(
              "Referencing module dependencies contains $id: $id",
              getErrorInfo(),
            ))
            continue
          }
          referencingModuleInfo.dependencies.add(ref)
          continue
        }

        if (referencingModuleInfo.isPlugin) {
          _errors.add(PluginValidationError(
            "Unsupported dependency type: ${child.name}",
            getErrorInfo(),
          ))
          continue
        }
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        _errors.add(PluginValidationError(
          "Module name is not specified",
          getErrorInfo(),
        ))
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        continue
      }

      if (child.attributes.size > 1) {
        _errors.add(PluginValidationError(
          "Unknown attributes: ${child.attributes.entries.filter { it.key != "name" }}",
          getErrorInfo(),
        ))
        continue
      }

      val moduleInfo = moduleNameToInfo.get(moduleName)
      if (moduleInfo == null) {
        val moduleDescriptorFileInfo = sourceModuleNameToFileInfo.get(moduleName)
        if (moduleDescriptorFileInfo != null) {
          if (moduleDescriptorFileInfo.pluginDescriptor != null) {
            _errors.add(PluginValidationError(
              message = "Dependency on plugin must be specified using `plugin` and not `module`",
              params = getErrorInfo(),
              fix = """
                    Change dependency element to:
                    
                    <plugin id="${moduleDescriptorFileInfo.pluginDescriptor!!.getChild("id")?.content}"/>
                  """,
            ))
            continue
          }
        }
        if (!moduleName.startsWith("kotlin.")) {
          // kotlin modules are loaded via conditional includes and the test cannot detect them
          _errors.add(PluginValidationError("Module not found: $moduleName", getErrorInfo()))
        }
        continue
      }

      referencingModuleInfo.dependencies.add(Reference(moduleName, isPlugin = false, moduleInfo))

      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          _errors.add(PluginValidationError(
            "Module, that used as dependency, must be not specified in `depends`",
            getErrorInfo(),
          ))
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
      fun getErrorInfo(): Map<String, Any> {
        return mapOf(
          "entry" to child,
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        )
      }

      if (child.name != "module") {
        _errors.add(PluginValidationError(
          "Unexpected element: $child",
          getErrorInfo(),
        ))
        continue
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        _errors.add(PluginValidationError(
          "Module name is not specified",
          getErrorInfo(),
        ))
        continue
      }

      val moduleLoadingRule = child.getAttributeValue("loading")
      if (moduleLoadingRule != null && moduleLoadingRule !in arrayOf("required", "optional", "on-demand")) {
        _errors.add(PluginValidationError(
          "Unknown value for 'loading' attribute: $moduleLoadingRule. Supported values are 'required', 'optional' and 'on-demand'.",
          getErrorInfo(),
        ))
        continue
      }

      if (child.attributes.size > 2) {
        _errors.add(PluginValidationError(
          "Unknown attributes: ${child.attributes.entries.filter { it.key != "name" || it.key != "loading" }}",
          getErrorInfo(),
        ))
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        _errors.add(PluginValidationError(
          "intellij.platform.commercial.verifier is not supposed to be used as content of plugin",
          getErrorInfo(),
        ))
        continue
      }

      // ignore null - getModule reports error
      val moduleDescriptorFileInfo = getModuleDescriptorFileInfo(moduleName, referencingModuleInfo, sourceModuleNameToFileInfo) ?: continue

      val moduleDescriptor = requireNotNull(moduleDescriptorFileInfo.moduleDescriptor) {
        "No module descriptor ($moduleDescriptorFileInfo)"
      }
      val moduleInfo = checkModuleFileInfo(moduleDescriptorFileInfo, moduleName, moduleNameToInfo) ?: continue
      referencingModuleInfo.content.add(moduleInfo)

      // check that not specified using `depends` tag
      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          _errors.add(PluginValidationError(
            "Module must be not specified in `depends`.",
            getErrorInfo() + mapOf(
              "referencedDescriptorFile" to moduleInfo.descriptorFile
            ),
          ))
          continue
        }
      }

      moduleDescriptor.getChild("content")?.let {
        _errors.add(PluginValidationError(
          "Module cannot define content",
          getErrorInfo() + mapOf(
            "referencedDescriptorFile" to moduleInfo.descriptorFile
          ),
        ))
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
      sourceModuleName = moduleDescriptorFileInfo.sourceModule.name,
      descriptorFile = moduleDescriptorFileInfo.moduleDescriptorFile!!,
      packageName = moduleDescriptor.getAttributeValue("package"),
      descriptor = moduleDescriptor,
    )
    moduleNameToInfo.put(moduleName, moduleInfo)
    return moduleInfo
  }

  private fun getModuleDescriptorFileInfo(moduleName: String,
                                          referencingModuleInfo: ModuleInfo,
                                          sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>): ModuleDescriptorFileInfo? {
    var module = sourceModuleNameToFileInfo.get(moduleName)
    if (module != null) {
      return module
    }

    fun getErrorInfo(): Map<String, Path> {
      return mapOf(
        "referencingDescriptorFile" to referencingModuleInfo.descriptorFile
      )
    }

    val prefix = referencingModuleInfo.sourceModuleName + "/"
    if (!moduleName.startsWith(prefix)) {
      val i = moduleName.indexOf("/")
      val message = if (i > -1) "$moduleName can only be accessed from ${moduleName.substring(0, i)}" else  "Cannot find module $moduleName"
      _errors.add(PluginValidationError(message,
        getErrorInfo(),
      ))
      return null
    }

    val slashIndex = prefix.length - 1
    val containingModuleName = moduleName.substring(0, slashIndex)
    module = sourceModuleNameToFileInfo[containingModuleName]
    if (module == null) {
      _errors.add(PluginValidationError(
        "Cannot find module $containingModuleName",
        getErrorInfo(),
      ))
      return null
    }

    val fileName = "$containingModuleName.${moduleName.substring(slashIndex + 1)}.xml"
    val result = loadFileInModule(sourceModule = module.sourceModule, fileName = fileName)
    if (result == null) {
      _errors.add(PluginValidationError(
        message = "Module ${module.sourceModule.name} doesn't have descriptor file",
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

  private fun updateFileInfo(
    moduleName: String,
    sourceRoot: Path,
    fileInfo: ModuleDescriptorFileInfo,
  ) {
    val metaInf = sourceRoot.resolve("META-INF")
    val moduleXml = metaInf.resolve("$moduleName.xml")
    if (Files.exists(moduleXml)) {
      _errors.add(PluginValidationError(
        "Module descriptor must be in the root of module root",
        mapOf(
          "module" to moduleName,
          "moduleDescriptor" to moduleXml,
        ),
      ))
      return
    }

    val pluginFileName = when (moduleName) {
      "intellij.platform.backend.split" -> "pluginBase.xml"
      "intellij.idea.community.customization" -> "IdeaPlugin.xml"
      else -> "plugin.xml"
    }
    val pluginDescriptorFile = metaInf.resolve(pluginFileName)
    val pluginDescriptor = readXmlAsModelOrNull(pluginDescriptorFile)

    val moduleDescriptorFile = sourceRoot.resolve("$moduleName.xml")
    val moduleDescriptor = readXmlAsModelOrNull(moduleDescriptorFile)

    if (pluginDescriptor == null && moduleDescriptor == null) {
      return
    }

    if (fileInfo.pluginDescriptorFile != null && pluginDescriptor != null) {
      _errors.add(PluginValidationError(
        "Duplicated plugin.xml",
        mapOf(
          "module" to moduleName,
          "firstPluginDescriptor" to fileInfo.pluginDescriptorFile,
          "secondPluginDescriptor" to pluginDescriptorFile,
        ),
      ))
      return
    }

    if (fileInfo.pluginDescriptorFile != null) {
      _errors.add(PluginValidationError(
        "Module cannot have both plugin.xml and module descriptor",
        mapOf(
          "module" to moduleName,
          "pluginDescriptor" to fileInfo.pluginDescriptorFile,
          "moduleDescriptor" to moduleDescriptorFile,
        ),
      ))
      return
    }

    if (fileInfo.moduleDescriptorFile != null && pluginDescriptor != null) {
      _errors.add(PluginValidationError(
        "Module cannot have both plugin.xml and module descriptor",
        mapOf(
          "module" to moduleName,
          "pluginDescriptor" to pluginDescriptorFile,
          "moduleDescriptor" to fileInfo.moduleDescriptorFile,
        ),
      ))
      return
    }

    if (pluginDescriptor == null) {
      fileInfo.moduleDescriptorFile = moduleDescriptorFile
      fileInfo.moduleDescriptor = moduleDescriptor
    }
    else {
      fileInfo.pluginDescriptorFile = pluginDescriptorFile
      fileInfo.pluginDescriptor = pluginDescriptor
    }
  }
}

internal data class ModuleInfo(
  @JvmField val pluginId: String?,
  @JvmField val name: String?,
  @JvmField val sourceModuleName: String,
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

internal data class Reference(@JvmField val name: String, @JvmField val isPlugin: Boolean, @JvmField val moduleInfo: ModuleInfo)

private data class ModuleDescriptorFileInfo(
  @JvmField val sourceModule: PluginModelValidator.Module,

  @JvmField var moduleDescriptor: XmlElement? = null,
  @JvmField var moduleDescriptorFile: Path? = null,
) {
  @JvmField var pluginDescriptorFile: Path? = null
  @JvmField var pluginDescriptor: XmlElement? = null
}

private fun writeModuleInfo(writer: JsonGenerator, item: ModuleInfo) {
  writer.obj {
    writer.writeStringField("name", item.name ?: item.sourceModuleName)
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

private class PluginValidationError private constructor(message: String) : RuntimeException(message) {
  constructor(
    message: String,
    params: Map<String, Any?> = mapOf(),
    fix: String? = null,
  ) : this(
    params.entries.joinToString(
      prefix = "$message (\n  ",
      separator = ",\n  ",
      postfix = "\n)" + (fix?.let { "\n\nProposed fix:\n\n" + fix.trimIndent() + "\n\n" } ?: "")
    ) {
      it.key + "=" + paramValueToString(it.value)
    }
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
  catch (ignore: NoSuchFileException) {
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