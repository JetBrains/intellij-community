// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.throwIfNotEmpty
import org.jdom.Element
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

private data class ModuleInfo(val descriptorFile: Path, val isPlugin: Boolean) {
  val dependencies = mutableListOf<Reference>()
  val content = mutableListOf<Reference>()
}

private data class Reference(val moduleName: String, val packageName: String)

private val homePath by lazy { Path.of(PathManager.getHomePath()) }

private data class PluginTask(val pluginId: String,
                              val moduleName: String,
                              val descriptor: Element,
                              val pluginXml: Path) {
  val moduleReferences = mutableListOf<Reference>()
}

private class ModuleDescriptorPath {
  var pluginDescriptorFile: Path? = null
  var moduleDescriptorFile: Path? = null

  var pluginDescriptor: Element? = null
  var moduleDescriptor: Element? = null
}

class PluginModelTest {
  @Test
  fun check() {
    val modules = IntelliJProjectConfiguration.loadIntelliJProject(homePath).modules
    val checker = PluginModelChecker(modules)

    // we cannot check everything in one pass - before checking we have to compute map of plugin to plugin modules
    // to be able to correctly check `<depends>Docker</depends>` that in a new plugin model specified as
    // <module name="intellij.clouds.docker.compose" package="com.intellij.docker.composeFile"/>

    val moduleNameToFileInfo = computeModuleSet(modules, checker)
    l@ for ((moduleName, moduleMetaInfo) in moduleNameToFileInfo) {
      val descriptor = moduleMetaInfo.pluginDescriptor ?: continue
      val pluginXml = moduleMetaInfo.pluginDescriptorFile ?: continue

      val id = descriptor.getChild("id")?.text
               ?: descriptor.getChild("name")?.text
      if (id == null) {
        checker.errors.add(PluginValidationError("Plugin id is not specified in ${homePath.relativize(pluginXml)}"))
        continue
      }

      val task = PluginTask(pluginId = id, moduleName = moduleName, descriptor = descriptor, pluginXml = pluginXml)
      checker.pluginIdToModules.put(id, task)

      val content = descriptor.getChild("content")
      if (content != null) {
        for (item in content.getChildren("module")) {
          // ignore missed attributes - will be validated on second pass
          val packageName = item.getAttributeValue("package") ?: continue
          task.moduleReferences.add(Reference(moduleName = item.getAttributeValue("name") ?: continue,
                                              packageName = packageName))

          val old = checker.packageToPlugin.put(packageName, task)
          if (old != null) {
            checker.errors.add(PluginValidationError("Duplicated package $packageName (old=$old, new=$task)"))
            continue@l
          }
        }
      }
    }

    for (task in checker.pluginIdToModules.values) {
      checkModule(task, checker)
    }

    throwIfNotEmpty(checker.errors)
    printGraph(checker)
  }

  private fun checkModule(info: PluginTask, checker: PluginModelChecker) {
    val descriptor = info.descriptor
    val dependencies = descriptor.getChild("dependencies")
    if (dependencies != null) {
      val pluginInfo = checker.graph.computeIfAbsent(info.moduleName) { ModuleInfo(info.pluginXml, isPlugin = true) }
      try {
        checker.checkDependencies(dependencies, descriptor, info.pluginXml, pluginInfo)
      }
      catch (e: PluginValidationError) {
        checker.errors.add(e)
      }
    }

    val content = descriptor.getChild("content")
    if (content != null) {
      val pluginInfo = checker.graph.computeIfAbsent(info.moduleName) { ModuleInfo(info.pluginXml, isPlugin = true) }
      try {
        checker.checkContent(content, descriptor, info.pluginXml, pluginInfo)
      }
      catch (e: PluginValidationError) {
        checker.errors.add(e)
      }
    }

    val aPackage = descriptor.getAttributeValue("package")
    if (aPackage == null && checker.graph.containsKey(info.moduleName) /* something was added */) {
      // some plugins cannot be yet fully migrated
      System.err.println("Package is not specified for ${info.moduleName} " +
                         "(pluginId=${info.pluginId}, descriptor=${homePath.relativize(info.pluginXml)})")
    }
  }
}

private fun computeModuleSet(modules: List<JpsModule>, checker: PluginModelChecker): LinkedHashMap<String, ModuleDescriptorPath> {
  val moduleNameToFileInfo = LinkedHashMap<String, ModuleDescriptorPath>()
  for (module in modules) {
    // platform/cwm-plugin/resources/META-INF/plugin.xml doesn't have id - ignore for now
    if (module.name.startsWith("fleet.") ||
        module.name == "fleet" ||
        module.name == "intellij.idea.ultimate.resources" ||
        module.name == "intellij.lightEdit" ||
        module.name == "intellij.webstorm" ||
        module.name == "intellij.cwm.plugin") {
      continue
    }

    for (sourceRoot in module.sourceRoots) {
      if (sourceRoot.rootType.isForTests) {
        continue
      }

      val root = Path.of(JpsPathUtil.urlToPath(sourceRoot.url))
      val metaInf = root.resolve("META-INF")
      val pluginDescriptorFile = metaInf.resolve("plugin.xml")
      val pluginDescriptor = try {
        JDOMUtil.load(pluginDescriptorFile)
      }
      catch (ignore: NoSuchFileException) {
        null
      }

      val moduleDescriptorFile = root.resolve("${module.name}.xml")
      val moduleDescriptor = try {
        JDOMUtil.load(moduleDescriptorFile)
      }
      catch (ignore: NoSuchFileException) {
        null
      }

      if (Files.exists(metaInf.resolve("${module.name}.xml"))) {
        checker.errors.add(PluginValidationError("Module descriptor must be in the root of module root", mapOf(
          "module" to module.name,
          "moduleDescriptor" to metaInf.resolve("${module.name}.xml"),
        )))
        continue
      }

      if (pluginDescriptor == null && moduleDescriptor == null) {
        continue
      }

      val item = moduleNameToFileInfo.computeIfAbsent(module.name) { ModuleDescriptorPath() }
      if (item.pluginDescriptorFile != null && pluginDescriptor != null) {
        checker.errors.add(PluginValidationError("Duplicated plugin.xml", mapOf(
          "module" to module.name,
          "firstPluginDescriptor" to item.pluginDescriptorFile,
          "secondPluginDescriptor" to pluginDescriptorFile,
        )))
        continue
      }
      if (item.pluginDescriptorFile != null && moduleDescriptor != null) {
        checker.errors.add(PluginValidationError("Module cannot have both plugin.xml and module descriptor", mapOf(
          "module" to module.name,
          "pluginDescriptor" to item.pluginDescriptorFile,
          "moduleDescriptor" to moduleDescriptorFile,
        )))
        continue
      }
      if (item.moduleDescriptorFile != null && pluginDescriptor != null) {
        checker.errors.add(PluginValidationError("Module cannot have both plugin.xml and module descriptor", mapOf(
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
  return moduleNameToFileInfo
}

private fun printGraph(checker: PluginModelChecker) {
  val stringWriter = StringWriter()
  val writer = JsonFactory().createGenerator(stringWriter)
  writer.useDefaultPrettyPrinter()
  val graph = checker.graph
  writer.use {
    writer.obj {
      val names = graph.keys.toTypedArray()
      names.sort()
      for (name in names) {
        writer.obj(name) {
          val item = graph.get(name)!!
          writer.writeStringField("descriptor", homePath.relativize(item.descriptorFile).toString())
          writeEntries(item.dependencies, "dependencies", writer)
          writeEntries(item.content, "content", writer)
        }
      }
    }
  }

  println(stringWriter.buffer)
  Files.writeString(Path.of("/tmp/plugin-report.json"), stringWriter.buffer)
}

private fun writeEntries(items: List<Reference>, jsonName: String, writer: JsonGenerator) {
  if (items.isNotEmpty()) {
    writer.array(jsonName) {
      for (entry in items) {
        writer.obj {
          writer.writeStringField("module", entry.moduleName)
          writer.writeStringField("modulePackage", entry.packageName)
        }
      }
    }
  }
}

private class PluginModelChecker(modules: List<JpsModule>) {
  val pluginIdToModules = LinkedHashMap<String, PluginTask>()
  val packageToPlugin = HashMap<String, PluginTask>()

  val graph = HashMap<String, ModuleInfo>()

  val errors = mutableListOf<Throwable>()

  private val nameToModule = modules.associateBy { it.name }

  fun checkDependencies(element: Element, referencingDescriptor: Element, referencingDescriptorFile: Path, pluginInfo: ModuleInfo) {
    for (child in element.children) {
      if (child.name != "module") {
        if (child.name == "plugin") {
          if (pluginInfo.isPlugin) {
            throw PluginValidationError("Plugin cannot depend on plugin: ${JDOMUtil.write(child)}",
                                        mapOf(
                                          "entry" to child,
                                          "descriptorFile" to referencingDescriptorFile,
                                        ))
          }

          // todo check that the referenced plugin exists
          continue
        }

        if (pluginInfo.isPlugin) {
          throw PluginValidationError("Unsupported dependency type: ${child.name}",
                                      mapOf(
                                        "entry" to child,
                                        "descriptorFile" to referencingDescriptorFile,
                                      ))
        }
      }

      val moduleName = child.getAttributeValue("name") ?: throw PluginValidationError("Module name is not specified")
      val module = nameToModule.get(moduleName) ?: throw PluginValidationError("Cannot find module $moduleName")

      if (moduleName == "intellij.platform.commercial.verifier") {
        continue
      }

      val (descriptor, referencedDescriptorFile) = loadFileInModule(module,
                                                                    referencingDescriptorFile = referencingDescriptorFile,
                                                                    child.getAttributeValue("package")!!)
      val aPackage = checkPackage(descriptor, referencedDescriptorFile, child)

      pluginInfo.dependencies.add(Reference(moduleName, aPackage))

      // check that also specified using depends tag
      var pluginDependency: String? = null
      for (dependsElement in referencingDescriptor.getChildren("depends")) {
        if (dependsElement.getAttributeValue("config-file") == "${module.name}.xml") {
          pluginDependency = dependsElement.text
          break
        }
      }

      if (pluginDependency == null) {
        //<dependencies>
        //  <!-- <depends>Docker</depends> -->
        //  <module name="intellij.clouds.docker.compose" package="com.intellij.docker.composeFile"/>
        //</dependencies>
        val task = packageToPlugin.get(aPackage)
        if (task != null) {
          for (dependsElement in referencingDescriptor.getChildren("depends")) {
            if (dependsElement.text == task.pluginId) {
              pluginDependency = task.pluginId
              break
            }
          }
        }
      }

      if (pluginDependency == null) {
        throw PluginValidationError("Module, that used as dependency, must be also specified in `depends` (during transition period)." +
                                    "\nPlease check that you use correct naming (not arbitrary file name, but exactly as module name plus `.xml` extension).",
                                    mapOf(
                                      "entry" to child,
                                      "referencingDescriptorFile" to referencingDescriptorFile,
                                      "referencedDescriptorFile" to referencedDescriptorFile
                                    ))
      }

      var isPluginDependencySpecified = false
      for (entryElement in descriptor.getChild("dependencies")?.getChildren("plugin") ?: emptyList()) {
        if (entryElement.getAttributeValue("id") == pluginDependency) {
          isPluginDependencySpecified = true
          break
        }
      }

      if (!isPluginDependencySpecified) {
        throw PluginValidationError("Module, that used as dependency, must specify dependency on some plugin (`dependencies.plugin`)" +
                                    "\n\nProposed fix (add to ${homePath.relativize(referencedDescriptorFile)}):\n\n" + """
                                      <dependencies>
                                        <plugin id="$pluginDependency"/>
                                      </dependencies>
                                    """.trimIndent() + "\n\n", mapOf(
          "entry" to child,
          "referencingDescriptorFile" to referencingDescriptorFile,
          "referencedDescriptorFile" to referencedDescriptorFile
        ))
      }

      descriptor.getChild("dependencies")?.let {
        try {
          checkDependencies(it, descriptor, referencedDescriptorFile,
                            graph.computeIfAbsent(moduleName) { ModuleInfo(referencedDescriptorFile, isPlugin = false) })
        }
        catch (e: PluginValidationError) {
          errors.add(e)
        }
      }
    }
  }

  // for plugin two variants:
  // 1) depends + dependency on plugin in a referenced descriptor = optional descriptor. In old format: depends tag
  // 2) no depends + no dependency on plugin in a referenced descriptor = directly injected into plugin (separate classloader is not created
  // during transition period). In old format: xi:include (e.g. <xi:include href="dockerfile-language.xml"/>.
  fun checkContent(content: Element,
                   referencingDescriptor: Element,
                   referencingDescriptorFile: Path,
                   pluginInfo: ModuleInfo) {
    for (child in content.children) {
      if (child.name != "module") {
        throw PluginValidationError("Unexpected element: ${JDOMUtil.write(child)}")
      }

      val moduleName = child.getAttributeValue("name") ?: throw PluginValidationError("Module name is not specified")
      val module = nameToModule.get(moduleName) ?: throw PluginValidationError("Cannot find module $moduleName")

      if (moduleName == "intellij.platform.commercial.verifier") {
        errors.add(PluginValidationError("intellij.platform.commercial.verifier is not supposed to be used as content of plugin",
                                         mapOf(
                                           "entry" to child,
                                           "referencingDescriptorFile" to referencingDescriptorFile,
                                         )))
        return
      }

      val (descriptor, referencedDescriptorFile) = loadFileInModule(module,
                                                                    referencingDescriptorFile,
                                                                    child.getAttributeValue("package")!!)
      val aPackage = checkPackage(descriptor, referencedDescriptorFile, child)

      pluginInfo.content.add(Reference(moduleName, aPackage))

      // check that also specified using depends tag
      var oldPluginDependencyId: String? = null
      for (dependsElement in referencingDescriptor.getChildren("depends")) {
        if (dependsElement.getAttributeValue("config-file") == "${module.name}.xml") {
          oldPluginDependencyId = dependsElement.text
          break
        }
      }

      var isPluginDependencyDeclared = false
      for (entryElement in descriptor.getChild("dependencies")?.getChildren("plugin") ?: emptyList()) {
        if (entryElement.getAttributeValue("id") == oldPluginDependencyId) {
          isPluginDependencyDeclared = true
          break
        }
      }

      // if dependency specified in a new format in the referenced descriptor, then must be also specified using old depends tag
      if (oldPluginDependencyId == null && isPluginDependencyDeclared) {
        throw PluginValidationError("Module, that used as plugin content and depends on a plugin, must be also specified in `depends` (during transition period)." +
                                    "\nPlease check that you use correct naming (not arbitrary file name, but exactly as module name plus `.xml` extension).",
                                    mapOf(
                                      "entry" to child,
                                      "referencingDescriptorFile" to referencingDescriptorFile,
                                      "referencedDescriptorFile" to referencedDescriptorFile
                                    ))
      }

      // if there is old depends tag, then dependency in a new format in the referenced descriptor must be also declared
      if (oldPluginDependencyId != null && !isPluginDependencyDeclared) {
        throw PluginValidationError("Module, that used as plugin content and depends on a plugin, must specify dependency on the plugin (`dependencies.plugin`)" +
                                    "\n\nProposed fix (add to ${homePath.relativize(referencedDescriptorFile)}):\n\n" + """
                                      <dependencies>
                                        <plugin id="$oldPluginDependencyId"/>
                                      </dependencies>
                                    """.trimIndent() + "\n\n", mapOf(
          "entry" to child,
          "referencingDescriptorFile" to referencingDescriptorFile,
          "referencedDescriptorFile" to referencedDescriptorFile
        ))
      }

      descriptor.getChild("content")?.let {
        try {
          checkContent(it, descriptor, referencedDescriptorFile,
                       graph.computeIfAbsent(moduleName) { ModuleInfo(referencedDescriptorFile, isPlugin = false) })
        }
        catch (e: PluginValidationError) {
          errors.add(e)
        }
      }
    }
  }
}

private fun checkPackage(descriptor: Element, descriptorFile: Path, child: Element): String {
  val aPackage = descriptor.getAttributeValue("package")
                 ?: throw PluginValidationError("package attribute is not specified", mapOf("descriptorFile" to descriptorFile))

  if (aPackage != child.getAttributeValue("package")) {
    throw PluginValidationError("package doesn't match", mapOf(
      "entry" to child,
      "referencedDescriptorFile" to descriptorFile,
      "packageInDescriptor" to aPackage
    ))
  }
  return aPackage
}

private class PluginValidationError(message: String) : RuntimeException(message) {
  constructor(message: String, params: Map<String, Any?>) : this(message + " (\n  ${params.entries.joinToString(separator = ",\n  ") { "${it.key}=${paramValueToString(it.value)}" }}\n)")

  constructor(message: String, params: Map<String, Any?>, fix: String) : this(
    message +
    " (\n  ${params.entries.joinToString(separator = ",\n  ") { "${it.key}=${paramValueToString(it.value)}" }}\n)" +
    "\n\nProposed fix:\n\n" + fix.trimIndent() + "\n\n"
  )
}

private fun paramValueToString(value: Any?): String {
  return when (value) {
    // reformat according to IJ XMl code style
    is Element -> JDOMUtil.write(value).replace("\" />", "\"/>")
    is Path -> homePath.relativize(value).toString()
    else -> value.toString()
  }
}

private fun loadFileInModule(module: JpsModule,
                             referencingDescriptorFile: Path,
                             aPackage: String): Pair<Element, Path> {
  val fileName = "${module.name}.xml"
  val roots = module.getSourceRoots(JavaResourceRootType.RESOURCE) + module.getSourceRoots(JavaSourceRootType.SOURCE)
  for (sourceRoot in roots) {
    try {
      val file = Path.of(JpsPathUtil.urlToPath(sourceRoot.url), fileName)
      return Pair(JDOMUtil.load(file), file)
    }
    catch (ignore: NoSuchFileException) {
    }
  }

  throw PluginValidationError("Module ${module.name} doesn't have descriptor file",
                              mapOf(
                                "expectedFile" to fileName,
                                "referencingDescriptorFile" to referencingDescriptorFile,
                              ),
                              """
                                Create file $fileName in ${homePath.relativize(Path.of(JpsPathUtil.urlToPath(roots.first().url)))}
                                with content:
                                
                                <idea-plugin package="$aPackage">
                                </idea-plugin>
                              """)
}

