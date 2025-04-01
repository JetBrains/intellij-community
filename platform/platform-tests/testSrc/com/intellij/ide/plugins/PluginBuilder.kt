// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.PluginXmlConst
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.io.Compressor
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import com.intellij.util.xml.dom.NoOpXmlInterner
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories

private val pluginIdCounter = AtomicInteger()

fun plugin(outDir: Path, @Language("XML") descriptor: String) {
  val rawDescriptor = try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("${rawDescriptor.id!!}/${PluginManagerCore.PLUGIN_XML_PATH}").write(descriptor.trimIndent())
}

fun dependencyXml(outDir: Path, ownerId: String, filename: String, @Language("XML") descriptor: String) {
   try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("${ownerId}/${PluginManagerCore.META_INF}${filename}").write(descriptor.trimIndent())
}

fun module(outDir: Path, ownerId: String, moduleId: String, @Language("XML") descriptor: String) {
  try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("$ownerId/$moduleId.xml").write(descriptor.trimIndent())
}

class PluginBuilder private constructor() {
  private data class ExtensionBlock(val ns: String, val text: String)
  private data class DependsTag(val pluginId: String, val configFile: String?)

  // counter is used to reduce plugin id length
  var id: String = "p_${pluginIdCounter.incrementAndGet()}"
    private set

  private var implementationDetail = false
  private var separateJar = false
  private var name: String? = null
  private var description: String? = null
  private var packagePrefix: String? = null
  private val dependsTags = mutableListOf<DependsTag>()
  private var applicationListeners: String? = null
  private var resourceBundleBaseName: String? = null
  private var actions: String? = null
  private val extensions = mutableListOf<ExtensionBlock>()
  private var extensionPoints: String? = null
  private var untilBuild: String? = null
  private var version: String? = null
  private val pluginAliases = mutableListOf<String>()

  private val content = mutableListOf<PluginContentDescriptor.ModuleItem>()
  private val dependencies = mutableListOf<ModuleDependencies.ModuleReference>()
  private val pluginDependencies = mutableListOf<ModuleDependencies.PluginReference>()
  private val incompatibleWith = mutableListOf<ModuleDependencies.PluginReference>()

  private data class SubDescriptor(val filename: String, val builder: PluginBuilder)
  private val subDescriptors = ArrayList<SubDescriptor>()

  fun dependsIntellijModulesLang(): PluginBuilder {
    depends("com.intellij.modules.lang")
    return this
  }

  fun id(id: String): PluginBuilder {
    this.id = id
    return this
  }

  fun randomId(idPrefix: String): PluginBuilder {
    this.id = "${idPrefix}_${pluginIdCounter.incrementAndGet()}"
    return this
  }

  fun name(name: String): PluginBuilder {
    this.name = name
    return this
  }

  fun description(description: String): PluginBuilder {
    this.description = description
    return this
  }

  fun packagePrefix(value: String?): PluginBuilder {
    packagePrefix = value
    return this
  }

  fun separateJar(value: Boolean): PluginBuilder {
    separateJar = value
    return this
  }

  fun depends(pluginId: String, configFile: String? = null): PluginBuilder {
    dependsTags.add(DependsTag(pluginId, configFile))
    return this
  }

  fun depends(pluginId: String, subDescriptor: PluginBuilder, filename: String? = null): PluginBuilder {
    val fileName = filename ?: "dep_${pluginIdCounter.incrementAndGet()}.xml"
    subDescriptors.add(SubDescriptor(PluginManagerCore.META_INF + fileName, subDescriptor))
    depends(pluginId, fileName)
    return this
  }

  fun module(moduleName: String, moduleDescriptor: PluginBuilder, loadingRule: ModuleLoadingRule = ModuleLoadingRule.OPTIONAL,
             moduleFile: String = "$moduleName.xml"): PluginBuilder {
    subDescriptors.add(SubDescriptor(moduleFile, moduleDescriptor))
    content.add(PluginContentDescriptor.ModuleItem(name = moduleName, configFile = null, descriptorContent = null, loadingRule = loadingRule))
    return this
  }

  fun pluginAlias(alias: String): PluginBuilder {
    pluginAliases.add(alias)
    return this
  }

  fun dependency(moduleName: String): PluginBuilder {
    dependencies.add(ModuleDependencies.ModuleReference(moduleName))
    return this
  }

  fun pluginDependency(pluginId: String): PluginBuilder {
    pluginDependencies.add(ModuleDependencies.PluginReference(PluginId.getId(pluginId)))
    return this
  }

  fun incompatibleWith(pluginId: String): PluginBuilder {
    incompatibleWith.add(ModuleDependencies.PluginReference(PluginId.getId(pluginId)))
    return this
  }

  fun resourceBundle(resourceBundle: String?): PluginBuilder {
    resourceBundleBaseName = resourceBundle
    return this
  }

  fun untilBuild(buildNumber: String): PluginBuilder {
    untilBuild = buildNumber
    return this
  }

  fun version(version: String): PluginBuilder {
    this.version = version
    return this
  }

  fun applicationListeners(text: String): PluginBuilder {
    applicationListeners = text
    return this
  }

  fun actions(text: String): PluginBuilder {
    actions = text
    return this
  }

  fun extensions(text: String, ns: String = "com.intellij"): PluginBuilder {
    extensions.add(ExtensionBlock(ns, text))
    return this
  }

  fun extensionPoints(@Language("XML") text: String): PluginBuilder {
    extensionPoints = text
    return this
  }

  fun implementationDetail(): PluginBuilder {
    implementationDetail = true
    return this
  }

  fun text(requireId: Boolean = true): String {
    return buildString {
      append("<idea-plugin")
      if (implementationDetail) {
        append(""" ${PluginXmlConst.PLUGIN_IMPLEMENTATION_DETAIL_ATTR}="true"""")
      }
      packagePrefix?.let {
        append(""" ${PluginXmlConst.PLUGIN_PACKAGE_ATTR}="$it"""")
      }
      if (separateJar) {
        append(""" separate-jar="true"""") // todo change to const from xml reader
      }
      append(">")
      if (requireId) {
        append("<id>$id</id>")
      }
      name?.let { append("<name>$it</name>") }
      description?.let { append("<description>$it</description>") }
      for (dependsTag in dependsTags) {
        val configFile = dependsTag.configFile
        if (configFile != null) {
          append("""<depends optional="true" config-file="$configFile">${dependsTag.pluginId}</depends>""")
        }
        else {
          append("<depends>${dependsTag.pluginId}</depends>")
        }
      }
      version?.let { append("<version>$it</version>") }
      if (untilBuild != null) {
        append("""<idea-version until-build="${untilBuild}"/>""")
      }
      for (extensionBlock in extensions) {
        append("""<extensions defaultExtensionNs="${extensionBlock.ns}">${extensionBlock.text}</extensions>""")
      }
      extensionPoints?.let { append("<extensionPoints>$it</extensionPoints>") }
      applicationListeners?.let { append("<applicationListeners>$it</applicationListeners>") }
      resourceBundleBaseName?.let { append("""<resource-bundle>$it</resource-bundle>""") }
      actions?.let { append("<actions>$it</actions>") }

      if (content.isNotEmpty()) {
        append("\n<content>\n  ")
        content.joinTo(this, separator = "\n  ") { moduleItem ->
          val loadingAttribute = when (moduleItem.loadingRule) {
            ModuleLoadingRule.OPTIONAL -> ""
            ModuleLoadingRule.REQUIRED -> "loading=\"required\" "
            ModuleLoadingRule.EMBEDDED -> "loading=\"embedded\" "
            ModuleLoadingRule.ON_DEMAND -> "loading=\"on-demand\" "
          }
          """<module name="${moduleItem.name}" $loadingAttribute/>""" 
        }
        append("\n</content>")
      }

      if (incompatibleWith.isNotEmpty()) {
        incompatibleWith.joinTo(this, separator = "\n  ") {
          """<incompatible-with>${it.id}</incompatible-with>"""
        }
      }

      if (dependencies.isNotEmpty() || pluginDependencies.isNotEmpty()) {
        append("\n<dependencies>\n  ")
        if (dependencies.isNotEmpty()) {
          dependencies.joinTo(this, separator = "\n  ") { """<module name="${it.name}" />""" }
        }
        if (pluginDependencies.isNotEmpty()) {
          pluginDependencies.joinTo(this, separator = "\n  ") { """<plugin id="${it.id}" />""" }
        }
        append("\n</dependencies>")
      }

      for (alias in pluginAliases) {
        append("\n")
        append("""<module value="$alias"/>""")
      }

      append("</idea-plugin>")
    }
  }

  fun build(path: Path): PluginBuilder {
    val allDescriptors = collectAllSubDescriptors(subDescriptors).toList()
    if (allDescriptors.any { it.builder.separateJar }) {
      val modulesDir = path.resolve("lib/modules")
      modulesDir.createDirectories()
      buildJar(path.resolve("lib/$id.jar"))
      for ((fileName, subDescriptor) in allDescriptors) {
        if (subDescriptor.separateJar) {
          val jarPath = modulesDir.resolve("${fileName.removeSuffix(".xml")}.jar")
          subDescriptor.buildJarToStream(Files.newOutputStream(jarPath), mainDescriptorRelativePath = fileName)
        }
      }
    }  
    else {
      path.resolve(PluginManagerCore.PLUGIN_XML_PATH).write(text())
      for (subDescriptor in allDescriptors) {
        path.resolve(subDescriptor.filename).createParentDirectories().write(subDescriptor.builder.text(requireId = false))
      }
    }
    return this
  }

  private fun collectAllSubDescriptors(descriptors: List<SubDescriptor>): Sequence<SubDescriptor> {
    return descriptors.asSequence().flatMap { sequenceOf(it) + collectAllSubDescriptors(it.builder.subDescriptors) } 
  }

  fun buildJar(path: Path): PluginBuilder {
    buildJarToStream(Files.newOutputStream(path), PluginManagerCore.PLUGIN_XML_PATH)
    return this
  }

  private fun buildJarToStream(outputStream: OutputStream, mainDescriptorRelativePath: String) {
    Compressor.Zip(outputStream).use {
      it.addFile(mainDescriptorRelativePath, text(requireId = mainDescriptorRelativePath == PluginManagerCore.PLUGIN_XML_PATH).toByteArray())
      for ((fileName, subDescriptor) in subDescriptors) {
        if (!subDescriptor.separateJar) {
          it.addFile(fileName, subDescriptor.text(requireId = false).toByteArray())
        }
      }
    }
  }

  fun buildZip(path: Path): PluginBuilder {
    val jarStream = ByteArrayOutputStream()
    buildJarToStream(jarStream, PluginManagerCore.PLUGIN_XML_PATH)

    val pluginName = name ?: id
    Compressor.Zip(Files.newOutputStream(path)).use {
      it.addDirectory(pluginName)
      it.addDirectory("$pluginName/lib")
      it.addFile("$pluginName/lib/$pluginName.jar", jarStream.toByteArray())
    }

    return this
  }

  companion object {
    fun withModulesLang(): PluginBuilder = PluginBuilder().dependsIntellijModulesLang()
    fun empty(): PluginBuilder = PluginBuilder()
  }
}

@TestOnly
fun readModuleDescriptorForTest(input: ByteArray): PluginDescriptorBuilder {
  return PluginDescriptorFromXmlStreamConsumer(readContext = object : ReadModuleContext {
    override val interner = NoOpXmlInterner
    override val isMissingIncludeIgnored = false
    override val elementOsFilter: (OS) -> Boolean
      get() = { it.convert().isSuitableForOs() }
  }, xIncludeLoader = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER.toXIncludeLoader(object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()
    override fun toString() = ""
  })).let {
    it.consume(input, null)
    it.getBuilder()
  }
}
