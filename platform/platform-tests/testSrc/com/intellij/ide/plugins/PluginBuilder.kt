// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.Compressor
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

class PluginBuilder {
  private data class ExtensionBlock(val ns: String, val text: String)
  private data class DependsTag(val pluginId: String, val configFile: String?)

  // counter is used to reduce plugin id length
  var id: String = "p_${pluginIdCounter.incrementAndGet()}"
    private set

  private var implementationDetail = false
  private var name: String? = null
  private var description: String? = null
  private var packagePrefix: String? = null
  private val dependsTags = mutableListOf<DependsTag>()
  private var applicationListeners: String? = null
  private var actions: String? = null
  private val extensions = mutableListOf<ExtensionBlock>()
  private var extensionPoints: String? = null
  private var untilBuild: String? = null
  private var version: String? = null

  private val content = mutableListOf<PluginContentDescriptor.ModuleItem>()
  private val dependencies = mutableListOf<ModuleDependenciesDescriptor.ModuleReference>()
  private val pluginDependencies = mutableListOf<ModuleDependenciesDescriptor.PluginReference>()

  private data class SubDescriptor(val filename: String, val builder: PluginBuilder, val separateJar: Boolean)
  private val subDescriptors = ArrayList<SubDescriptor>()

  init {
    depends("com.intellij.modules.lang")
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

  fun depends(pluginId: String, configFile: String? = null): PluginBuilder {
    dependsTags.add(DependsTag(pluginId, configFile))
    return this
  }

  fun depends(pluginId: String, subDescriptor: PluginBuilder): PluginBuilder {
    val fileName = "dep_${pluginIdCounter.incrementAndGet()}.xml"
    subDescriptors.add(SubDescriptor(PluginManagerCore.META_INF + fileName, subDescriptor, separateJar = false))
    depends(pluginId, fileName)
    return this
  }

  fun module(moduleName: String, moduleDescriptor: PluginBuilder, loadingRule: ModuleLoadingRule = ModuleLoadingRule.OPTIONAL,
             separateJar: Boolean = false): PluginBuilder {
    val fileName = "$moduleName.xml"
    subDescriptors.add(SubDescriptor(fileName, moduleDescriptor, separateJar))
    content.add(PluginContentDescriptor.ModuleItem(name = moduleName, configFile = null, descriptorContent = null, loadingRule = loadingRule))

    // remove default dependency on lang
    moduleDescriptor.noDepends()
    return this
  }

  fun dependency(moduleName: String): PluginBuilder {
    dependencies.add(ModuleDependenciesDescriptor.ModuleReference(moduleName))
    return this
  }

  fun pluginDependency(pluginId: String): PluginBuilder {
    pluginDependencies.add(ModuleDependenciesDescriptor.PluginReference(PluginId.getId(pluginId)))
    return this
  }

  fun noDepends(): PluginBuilder {
    dependsTags.clear()
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
        append(""" $IMPLEMENTATION_DETAIL_ATTRIBUTE="true"""")
      }
      packagePrefix?.let {
        append(""" $PACKAGE_ATTRIBUTE="$it"""")
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
      actions?.let { append("<actions>$it</actions>") }

      if (content.isNotEmpty()) {
        append("\n<content>\n  ")
        content.joinTo(this, separator = "\n  ") { moduleItem ->
          val loadingAttribute = when (moduleItem.loadingRule) {
            ModuleLoadingRule.OPTIONAL -> ""
            ModuleLoadingRule.REQUIRED -> "loading=\"required\" "
            ModuleLoadingRule.ON_DEMAND -> "loading=\"on-demand\" "
          }
          """<module name="${moduleItem.name}" $loadingAttribute/>""" 
        }
        append("\n</content>")
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

      append("</idea-plugin>")
    }
  }

  fun build(path: Path): PluginBuilder {
    val allDescriptors = collectAllSubDescriptors(subDescriptors).toList()
    if (allDescriptors.any { it.separateJar }) {
      val modulesDir = path.resolve("lib/modules")
      modulesDir.createDirectories()
      buildJar(path.resolve("lib/$id.jar"))
      for ((fileName, subDescriptor, separateJar) in allDescriptors) {
        if (separateJar) {
          val jarPath = modulesDir.resolve("${fileName.removeSuffix(".xml")}.jar")
          subDescriptor.buildJarToStream(Files.newOutputStream(jarPath), mainDescriptorRelativePath = fileName)
        }
      }
    }  
    else {
      path.resolve(PluginManagerCore.PLUGIN_XML_PATH).write(text())
      for (subDescriptor in allDescriptors) {
        path.resolve(subDescriptor.filename).write(subDescriptor.builder.text(requireId = false))
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
      for ((fileName, subDescriptor, separateJar) in subDescriptors) {
        if (!separateJar) {
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
}

@TestOnly
fun readModuleDescriptorForTest(input: ByteArray): RawPluginDescriptor = readModuleDescriptor(
  input,
  object : ReadModuleContext {
    override val interner = NoOpXmlInterner
    override val isMissingIncludeIgnored = false
  },
  PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()
    override fun toString() = ""
  },
  includeBase = null,
  readInto = null,
  locationSource = null,
)
