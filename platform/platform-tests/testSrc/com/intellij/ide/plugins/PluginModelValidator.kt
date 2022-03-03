// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.PathManager.getHomePath
import com.intellij.util.XmlElement
import com.intellij.util.getErrorsAsString
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.readXmlAsModel
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div

private val emptyPath by lazy {
  Path.of("/")
}

internal val homePath by lazy {
  Path.of(getHomePath())
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val moduleSkipList = java.util.Set.of(
  "fleet",
  "intellij.indexing.shared.ultimate.plugin.internal.generator",
  "intellij.indexing.shared.ultimate.plugin.public",
  "kotlin-ultimate.appcode-kmm.main", /* Used only when running from sources */
  "intellij.javaFX.community",
  "intellij.lightEdit",
  "intellij.webstorm",
  "intellij.cwm.plugin", /* platform/cwm-plugin/resources/META-INF/plugin.xml doesn't have `id` - ignore for now */
  "intellij.osgi", /* no particular package prefix to choose */
  "intellij.hunspell", /* MP-3656 Marketplace doesn't allow uploading plugins without dependencies */
)

class PluginModelValidator(sourceModules: List<Module>) {
  interface Module {
    val name: String

    val sourceRoots: List<Path>
  }

  private val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()

  private val _errors = mutableListOf<Throwable>()

  val errors: List<Throwable>
    get() = java.util.List.copyOf(_errors)

  val errorsAsString: CharSequence
    get() =
      if (_errors.isEmpty()) ""
      else
        getErrorsAsString(_errors, includeStackTrace = false)

  init {
    // 1. collect plugin and module file info set
    val sourceModuleNameToFileInfo = sourceModules.associate {
      it.name to ModuleDescriptorFileInfo(it)
    }

    for (module in sourceModules) {
      val moduleName = module.name
      if (moduleName.startsWith("fleet.")
          || moduleSkipList.contains(moduleName)) {
        continue
      }

      for (sourceRoot in module.sourceRoots) {
        updateFileInfo(
          moduleName,
          sourceRoot,
          sourceModuleNameToFileInfo[moduleName]!!
        )
      }
    }

    val moduleNameToInfo = HashMap<String, ModuleInfo>()
    // 2. process plugins - process content to collect modules
    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      // interested only in plugins
      val descriptor = moduleMetaInfo.pluginDescriptor ?: continue
      val descriptorFile = moduleMetaInfo.pluginDescriptorFile ?: continue

      val id = descriptor.getChild("id")?.content ?: descriptor.getChild("name")?.content
      if (id == null) {
        _errors.add(PluginValidationError(
          "Plugin id is not specified",
          mapOf(
            "descriptorFile" to descriptorFile
          ),
        ))
        continue
      }

      val moduleInfo = ModuleInfo(pluginId = id,
                                  name = null,
                                  sourceModuleName = sourceModuleName,
                                  descriptorFile = descriptorFile,
                                  packageName = descriptor.getAttributeValue("package"),
                                  descriptor = descriptor)
      val prev = pluginIdToInfo.put(id, moduleInfo)
      if (prev != null) {
        throw PluginValidationError(
          "Duplicated plugin id: $id",
          mapOf(
            "prev" to prev,
            "current" to moduleInfo,
          ),
        )
      }

      descriptor.getChild("content")?.let { contentElement ->
        checkContent(content = contentElement,
                     referencingModuleInfo = moduleInfo,
                     sourceModuleNameToFileInfo = sourceModuleNameToFileInfo,
                     moduleNameToInfo = moduleNameToInfo)
      }
    }

    // 3. check dependencies - we are aware about all modules now
    for (pluginInfo in pluginIdToInfo.values) {
      val descriptor = pluginInfo.descriptor

      val dependenciesElements = descriptor.getChildren("dependencies")
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

      // in the end after processing content and dependencies
      if (pluginInfo.packageName == null && hasContentOrDependenciesInV2Format(descriptor)) {
        // some plugins cannot be yet fully migrated
        val error = PluginValidationError(
          "Plugin ${pluginInfo.pluginId} is not fully migrated: package is not specified",
          mapOf(
            "pluginId" to pluginInfo.pluginId,
            "descriptor" to pluginInfo.descriptorFile,
          ),
        )
        System.err.println(error.message)
      }

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
          checkDependencies(dependencies, contentModuleInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo)
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
    if (referencingModuleInfo.packageName == null && !knownNotFullyMigratedPluginIds.contains(referencingModuleInfo.pluginId)) {
      _errors.add(PluginValidationError(
        "`dependencies` must be specified only for plugin in a new format: package prefix is not specified",
        mapOf(
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        )))
    }

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

      val moduleInfo = moduleNameToInfo[moduleName]
      if (moduleInfo == null) {
        val moduleDescriptorFileInfo = sourceModuleNameToFileInfo[moduleName]
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
        _errors.add(PluginValidationError("Module not found: $moduleName", getErrorInfo()))
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

  // for plugin two variants:
  // 1) depends + dependency on plugin in a referenced descriptor = optional descriptor. In old format: depends tag
  // 2) no depends + no dependency on plugin in a referenced descriptor = directly injected into plugin (separate classloader is not created
  // during transition period). In old format: xi:include (e.g. <xi:include href="dockerfile-language.xml"/>).
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
      if (child.attributes.size > 1) {
        _errors.add(PluginValidationError(
          "Unknown attributes: ${child.attributes.entries.filter { it.key != "name" }}",
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

      val moduleDescriptor = moduleDescriptorFileInfo.moduleDescriptor!!
      val packageName = moduleDescriptor.getAttributeValue("package")
      if (packageName == null) {
        _errors.add(PluginValidationError(
          "Module package is not specified",
          mapOf(
            "descriptorFile" to moduleDescriptorFileInfo.moduleDescriptorFile!!,
          ),
        ))
        continue
      }

      val moduleInfo = ModuleInfo(pluginId = null,
                                  name = moduleName,
                                  sourceModuleName = moduleDescriptorFileInfo.sourceModule.name,
                                  descriptorFile = moduleDescriptorFileInfo.moduleDescriptorFile!!,
                                  packageName = packageName,
                                  descriptor = moduleDescriptor)
      moduleNameToInfo[moduleName] = moduleInfo
      referencingModuleInfo.content.add(moduleInfo)

      @Suppress("GrazieInspection")
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

  private fun getModuleDescriptorFileInfo(moduleName: String,
                                          referencingModuleInfo: ModuleInfo,
                                          sourceModuleNameToFileInfo: Map<String, ModuleDescriptorFileInfo>): ModuleDescriptorFileInfo? {
    var module = sourceModuleNameToFileInfo[moduleName]
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
      _errors.add(PluginValidationError(
        "Cannot find module $moduleName",
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
    val metaInf = sourceRoot / "META-INF"
    val moduleXml = metaInf / "$moduleName.xml"
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

    val pluginDescriptorFile = metaInf / "plugin.xml"
    val pluginDescriptor = pluginDescriptorFile.readXmlAsModel()

    val moduleDescriptorFile = sourceRoot / "$moduleName.xml"
    val moduleDescriptor = moduleDescriptorFile.readXmlAsModel()

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
  val pluginId: String?,
  val name: String?,
  val sourceModuleName: String,
  val descriptorFile: Path,
  val packageName: String?,

  val descriptor: XmlElement,
) {
  val content = mutableListOf<ModuleInfo>()
  val dependencies = mutableListOf<Reference>()

  val isPlugin: Boolean
    get() = pluginId != null
}

internal data class Reference(val name: String, val isPlugin: Boolean, val moduleInfo: ModuleInfo)

private data class PluginInfo(val pluginId: String,
                              val sourceModuleName: String,
                              val descriptor: XmlElement,
                              val descriptorFile: Path)

private class ModuleDescriptorFileInfo(val sourceModule: PluginModelValidator.Module) {
  var pluginDescriptorFile: Path? = null
  var moduleDescriptorFile: Path? = null

  var pluginDescriptor: XmlElement? = null
  var moduleDescriptor: XmlElement? = null
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
    val moduleDescriptorFile = sourceRoot / fileName
    val moduleDescriptor = moduleDescriptorFile.readXmlAsModel()
    if (moduleDescriptor != null) {
      return ModuleDescriptorFileInfo(sourceModule).also {
        it.moduleDescriptor = moduleDescriptor
        it.moduleDescriptorFile = moduleDescriptorFile
      }
    }
  }
  return null
}

private fun Path.readXmlAsModel(): XmlElement? {
  return try {
    Files.newInputStream(this).use(::readXmlAsModel)
  }
  catch (ignore: NoSuchFileException) {
    null
  }
}

internal fun hasContentOrDependenciesInV2Format(descriptor: XmlElement): Boolean {
  return descriptor.children.any { it.name == "content" || it.name == "dependencies" }
}