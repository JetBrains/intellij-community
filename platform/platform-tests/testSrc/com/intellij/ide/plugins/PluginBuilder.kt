// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.Compressor
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

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

  private val subDescriptors = HashMap<String, PluginBuilder>()

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
    subDescriptors.put(PluginManagerCore.META_INF + fileName, subDescriptor)
    depends(pluginId, fileName)
    return this
  }

  fun module(moduleName: String, moduleDescriptor: PluginBuilder): PluginBuilder {
    val fileName = "$moduleName.xml"
    subDescriptors.put(fileName, moduleDescriptor)
    content.add(PluginContentDescriptor.ModuleItem(name = moduleName, configFile = null))

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
        content.joinTo(this, separator = "\n  ") { """<module name="${it.name}" />""" }
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
    path.resolve(PluginManagerCore.PLUGIN_XML_PATH).write(text())
    writeSubDescriptors(path)
    return this
  }

  fun writeSubDescriptors(path: Path) {
    for ((fileName, subDescriptor) in subDescriptors) {
      path.resolve(fileName).write(subDescriptor.text(requireId = false))
      subDescriptor.writeSubDescriptors(path)
    }
  }

  fun buildJar(path: Path): PluginBuilder {
    buildJarToStream(Files.newOutputStream(path))
    return this
  }

  private fun buildJarToStream(outputStream: OutputStream) {
    Compressor.Zip(outputStream).use {
      it.addFile(PluginManagerCore.PLUGIN_XML_PATH, text().toByteArray())
    }
  }

  fun buildZip(path: Path): PluginBuilder {
    val jarStream = ByteArrayOutputStream()
    buildJarToStream(jarStream)

    val pluginName = name ?: id
    Compressor.Zip(Files.newOutputStream(path)).use {
      it.addDirectory(pluginName)
      it.addDirectory("$pluginName/lib")
      it.addFile("$pluginName/lib/$pluginName.jar", jarStream.toByteArray())
    }

    return this
  }
}
