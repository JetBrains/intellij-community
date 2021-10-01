// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.ide.plugins.PluginModelValidator.Companion.homePath
import com.intellij.openapi.application.PathManager
import com.intellij.util.XmlElement
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.readXmlAsModel
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*

private val emptyPath = Path.of("/")

internal class PluginModelValidator {
  internal interface Module {
    val name: String

    fun getSourceRoots(): List<Path>
  }

  companion object {
    internal val homePath by lazy { Path.of(PathManager.getHomePath()) }
  }

  private val pluginIdToInfo = LinkedHashMap<String, ModuleInfo>()

  private val errors = mutableListOf<Throwable>()

  fun getErrors(): List<Throwable> = java.util.List.copyOf(errors)

  fun validate(sourceModules: List<Module>): List<Throwable> {
    // 1. collect plugin and module file info set
    val sourceModuleNameToFileInfo = computeModuleSet(sourceModules, errors)
    val moduleNameToInfo = HashMap<String, ModuleInfo>()
    // 2. process plugins - process content to collect modules
    for ((sourceModuleName, moduleMetaInfo) in sourceModuleNameToFileInfo) {
      // interested only in plugins
      val descriptor = moduleMetaInfo.pluginDescriptor ?: continue
      val descriptorFile = moduleMetaInfo.pluginDescriptorFile ?: continue

      val id = descriptor.getChild("id")?.content ?: descriptor.getChild("name")?.content
      if (id == null) {
        errors.add(PluginValidationError("Plugin id is not specified (descriptorFile=${pathToShortString(descriptorFile)})"))
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
        throw PluginValidationError("Duplicated plugin id: $id (prev=$prev, current=$moduleInfo)")
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
        errors.add(PluginValidationError(
          "The only `dependencies` tag is expected",
          mapOf(
            "descriptorFile" to pluginInfo.descriptorFile,
          )))
      }
      else if (dependenciesElements.size == 1) {
        checkDependencies(dependenciesElements.first(), pluginInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo)
      }

      // in the end after processing content and dependencies
      if (pluginInfo.packageName == null && hasContentOrDependenciesInV2Format(descriptor)) {
        // some plugins cannot be yet fully migrated
        System.err.println("Plugin ${pluginInfo.pluginId} is not fully migrated: package is not specified" +
                           " (pluginId=${pluginInfo.pluginId}, descriptor=${pathToShortString(pluginInfo.descriptorFile)})")
      }

      if (pluginInfo.packageName != null) {
        descriptor.children.firstOrNull { it.name == "depends" && it.getAttributeValue("optional") == null }?.let {
          errors.add(PluginValidationError(
            "Old format must be not used for a plugin with a specified package prefix but `depends` tag is used, use a new format, see " +
            "(https://github.com/JetBrains/intellij-community/blob/master/docs/plugin.md#the-dependencies-element)",
            mapOf(
              "descriptorFile" to pluginInfo.descriptorFile,
              "depends" to it,
            )))
        }
      }

      for (contentModuleInfo in pluginInfo.content) {
        contentModuleInfo.descriptor.getChild("dependencies")?.let { dependencies ->
          checkDependencies(dependencies, contentModuleInfo, pluginInfo, moduleNameToInfo, sourceModuleNameToFileInfo)
        }

        contentModuleInfo.descriptor.getChild("depends")?.let {
          errors.add(PluginValidationError(
            "Old format must be not used for a module but `depends` tag is used",
            mapOf(
              "descriptorFile" to contentModuleInfo.descriptorFile,
              "depends" to it,
            )))
        }
      }
    }
    return getErrors()
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
    if (referencingModuleInfo.packageName == null) {
      errors.add(PluginValidationError(
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
            errors.add(PluginValidationError("Id is not specified for dependency on plugin", getErrorInfo()))
            continue
          }
          if (id == "com.intellij.modules.java") {
            errors.add(PluginValidationError("Use com.intellij.modules.java id instead of com.intellij.modules.java", getErrorInfo()))
            continue
          }
          if (id == "com.intellij.modules.platform") {
            errors.add(PluginValidationError("No need to specify dependency on $id", getErrorInfo()))
            continue
          }
          if (id == referencingPluginInfo.pluginId) {
            errors.add(PluginValidationError("Do not add dependency on a parent plugin", getErrorInfo()))
            continue
          }

          val dependency = pluginIdToInfo.get(id)
          if (!id.startsWith("com.intellij.modules.") && dependency == null) {
            errors.add(PluginValidationError("Plugin not found: $id", getErrorInfo()))
            continue
          }

          val ref = Reference(
            name = id,
            isPlugin = true,
            moduleInfo = dependency ?: ModuleInfo(null, id, "", emptyPath, null,
                                                  XmlElement("", Collections.emptyMap(), Collections.emptyList(), null))
          )
          if (referencingModuleInfo.dependencies.contains(ref)) {
            errors.add(PluginValidationError("Referencing module dependencies contains $id: $id", getErrorInfo()))
            continue
          }
          referencingModuleInfo.dependencies.add(ref)
          continue
        }

        if (referencingModuleInfo.isPlugin) {
          errors.add(PluginValidationError("Unsupported dependency type: ${child.name}", getErrorInfo()))
          continue
        }
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        errors.add(PluginValidationError("Module name is not specified", getErrorInfo()))
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        continue
      }

      if (child.attributes.size > 1) {
        errors.add(PluginValidationError("Unknown attributes: ${child.attributes.entries.filter { it.key != "name" }}", getErrorInfo()))
        continue
      }

      val moduleInfo = moduleNameToInfo.get(moduleName)
      if (moduleInfo == null) {
        val moduleDescriptorFileInfo = sourceModuleNameToFileInfo.get(moduleName)
        if (moduleDescriptorFileInfo != null) {
          if (moduleDescriptorFileInfo.pluginDescriptor != null) {
            errors.add(PluginValidationError(
              message = "Dependency on plugin must be specified using `plugin` and not `module`",
              params = getErrorInfo(),
              fix = """
                    Change dependency element to:
                    
                    <plugin id="${moduleDescriptorFileInfo.pluginDescriptor!!.getChild("id")?.content}"/>
                  """
            ))
            continue
          }
        }
        errors.add(PluginValidationError("Module not found: $moduleName", getErrorInfo()))
        continue
      }

      referencingModuleInfo.dependencies.add(Reference(moduleName, isPlugin = false, moduleInfo))

      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          errors.add(PluginValidationError("Module, that used as dependency, must be not specified in `depends`", getErrorInfo()))
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
        errors.add(PluginValidationError("Unexpected element: $child", getErrorInfo()))
        continue
      }

      val moduleName = child.getAttributeValue("name")
      if (moduleName == null) {
        errors.add(PluginValidationError("Module name is not specified", getErrorInfo()))
        continue
      }
      if (child.attributes.size > 1) {
        errors.add(PluginValidationError("Unknown attributes: ${child.attributes.entries.filter { it.key != "name" }}", getErrorInfo()))
        continue
      }

      if (moduleName == "intellij.platform.commercial.verifier") {
        errors.add(PluginValidationError("intellij.platform.commercial.verifier is not supposed to be used as content of plugin",
                                         getErrorInfo()))
        continue
      }

      // ignore null - getModule reports error
      val moduleDescriptorFileInfo = getModuleDescriptorFileInfo(moduleName, referencingModuleInfo, sourceModuleNameToFileInfo) ?: continue

      val moduleDescriptor = moduleDescriptorFileInfo.moduleDescriptor!!
      val aPackage = moduleDescriptor.getAttributeValue("package")
      if (aPackage == null) {
        errors.add(PluginValidationError("Module package is not specified", mapOf(
          "descriptorFile" to moduleDescriptorFileInfo.moduleDescriptorFile!!,
        )))
        continue
      }

      val moduleInfo = ModuleInfo(pluginId = null,
                                  name = moduleName,
                                  sourceModuleName = moduleDescriptorFileInfo.sourceModule.name,
                                  descriptorFile = moduleDescriptorFileInfo.moduleDescriptorFile!!,
                                  packageName = aPackage,
                                  descriptor = moduleDescriptor)
      moduleNameToInfo.put(moduleName, moduleInfo)
      referencingModuleInfo.content.add(moduleInfo)

      @Suppress("GrazieInspection")
      // check that not specified using `depends` tag
      for (dependsElement in referencingModuleInfo.descriptor.children) {
        if (dependsElement.name != "depends") {
          continue
        }

        if (dependsElement.getAttributeValue("config-file")?.removePrefix("/META-INF/") == moduleInfo.descriptorFile.fileName.toString()) {
          errors.add(PluginValidationError(
            "Module must be not specified in `depends`.",
            getErrorInfo() + mapOf(
              "referencedDescriptorFile" to moduleInfo.descriptorFile
            )))
          continue
        }
      }

      moduleDescriptor.getChild("content")?.let {
        errors.add(PluginValidationError("Module cannot define content", getErrorInfo() + mapOf(
          "referencedDescriptorFile" to moduleInfo.descriptorFile
        )))
      }
    }
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

    val prefix = "${referencingModuleInfo.sourceModuleName}/"
    if (!moduleName.startsWith(prefix)) {
      errors.add(PluginValidationError("Cannot find module $moduleName", getErrorInfo()))
      return null
    }

    val slashIndex = prefix.length - 1
    val containingModuleName = moduleName.substring(0, slashIndex)
    module = sourceModuleNameToFileInfo.get(containingModuleName)
    if (module == null) {
      errors.add(PluginValidationError("Cannot find module $containingModuleName", getErrorInfo()))
      return null
    }

    val fileName = "$containingModuleName.${moduleName.substring(slashIndex + 1)}.xml"
    val result = loadFileInModule(sourceModule = module.sourceModule, fileName = fileName)
    if (result == null) {
      errors.add(PluginValidationError(
        message = "Module ${module.sourceModule.name} doesn't have descriptor file",
        params = mapOf(
          "expectedFile" to fileName,
          "referencingDescriptorFile" to referencingModuleInfo.descriptorFile,
        ),
        fix = """
              Create file $fileName in ${pathToShortString(module.sourceModule.getSourceRoots().first())}
              with content:
              
              <idea-plugin package="REPLACE_BY_MODULE_PACKAGE">
              </idea-plugin>
            """
      ))
    }
    return result
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

private fun computeModuleSet(sourceModules: List<PluginModelValidator.Module>,
                             errors: MutableList<Throwable>): LinkedHashMap<String, ModuleDescriptorFileInfo> {
  val sourceModuleNameToFileInfo = LinkedHashMap<String, ModuleDescriptorFileInfo>()
  for (module in sourceModules) {
    // platform/cwm-plugin/resources/META-INF/plugin.xml doesn't have `id` - ignore for now
    if (module.name.startsWith("fleet.") ||
        module.name == "fleet" ||
        // https://youtrack.jetbrains.com/issue/IDEA-261850
        module.name == "intellij.indexing.shared.ultimate.plugin.internal.generator" ||
        module.name == "intellij.indexing.shared.ultimate.plugin.public" ||
        module.name == "kotlin-ultimate.mobile-native.overrides" ||
        module.name == "kotlin-ultimate.appcode-with-mobile" ||
        module.name == "intellij.javaFX.community" ||
        module.name == "intellij.lightEdit" ||
        module.name == "intellij.webstorm" ||
        module.name == "intellij.cwm.plugin") {
      continue
    }

    for (sourceRoot in module.getSourceRoots()) {
      val metaInf = sourceRoot.resolve("META-INF")
      val pluginDescriptorFile = metaInf.resolve("plugin.xml")
      val pluginDescriptor = try {
        readXmlAsModel(Files.newInputStream(pluginDescriptorFile))
      }
      catch (ignore: NoSuchFileException) {
        null
      }

      val moduleDescriptorFile = sourceRoot.resolve("${module.name}.xml")
      val moduleDescriptor = try {
        readXmlAsModel(Files.newInputStream(moduleDescriptorFile))
      }
      catch (ignore: NoSuchFileException) {
        null
      }

      if (Files.exists(metaInf.resolve("${module.name}.xml"))) {
        errors.add(PluginValidationError("Module descriptor must be in the root of module root", mapOf(
          "module" to module.name,
          "moduleDescriptor" to metaInf.resolve("${module.name}.xml"),
        )))
        continue
      }

      if (pluginDescriptor == null && moduleDescriptor == null) {
        continue
      }

      val item = sourceModuleNameToFileInfo.computeIfAbsent(module.name) { ModuleDescriptorFileInfo(module) }
      if (item.pluginDescriptorFile != null && pluginDescriptor != null) {
        errors.add(PluginValidationError("Duplicated plugin.xml", mapOf(
          "module" to module.name,
          "firstPluginDescriptor" to item.pluginDescriptorFile,
          "secondPluginDescriptor" to pluginDescriptorFile,
        )))
        continue
      }

      if (item.pluginDescriptorFile != null && moduleDescriptor != null) {
        errors.add(PluginValidationError("Module cannot have both plugin.xml and module descriptor", mapOf(
          "module" to module.name,
          "pluginDescriptor" to item.pluginDescriptorFile,
          "moduleDescriptor" to moduleDescriptorFile,
        )))
        continue
      }

      if (item.moduleDescriptorFile != null && pluginDescriptor != null) {
        errors.add(PluginValidationError("Module cannot have both plugin.xml and module descriptor", mapOf(
          "module" to module.name,
          "pluginDescriptor" to pluginDescriptorFile,
          "moduleDescriptor" to item.moduleDescriptorFile,
        )))
        continue
      }

      if (pluginDescriptor == null) {
        item.moduleDescriptorFile = moduleDescriptorFile
        item.moduleDescriptor = moduleDescriptor
      }
      else {
        item.pluginDescriptorFile = pluginDescriptorFile
        item.pluginDescriptor = pluginDescriptor
      }
    }
  }
  return sourceModuleNameToFileInfo
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

private class PluginValidationError(message: String) : RuntimeException(message) {
  constructor(message: String, params: Map<String, Any?>) :
    this(message + " (\n  ${params.entries.joinToString(separator = ",\n  ") { "${it.key}=${paramValueToString(it.value)}" }}\n)")

  constructor(message: String, params: Map<String, Any?>, fix: String) : this(
    message +
    " (\n  ${params.entries.joinToString(separator = ",\n  ") { "${it.key}=${paramValueToString(it.value)}" }}\n)" +
    "\n\nProposed fix:\n\n" + fix.trimIndent() + "\n\n"
  )
}

private fun paramValueToString(value: Any?): String {
  return when (value) {
    is Path -> pathToShortString(value)
    else -> value.toString()
  }
}

private fun loadFileInModule(sourceModule: PluginModelValidator.Module, fileName: String): ModuleDescriptorFileInfo? {
  for (sourceRoot in sourceModule.getSourceRoots()) {
    try {
      val file = sourceRoot.resolve(fileName)
      val info = ModuleDescriptorFileInfo(sourceModule)
      info.moduleDescriptor = readXmlAsModel(Files.newInputStream(file))
      info.moduleDescriptorFile = file
      return info
    }
    catch (ignore: NoSuchFileException) {
    }
  }
  return null
}

internal fun hasContentOrDependenciesInV2Format(descriptor: XmlElement): Boolean {
  return descriptor.children.any { it.name == "content" || it.name == "dependencies" }
}