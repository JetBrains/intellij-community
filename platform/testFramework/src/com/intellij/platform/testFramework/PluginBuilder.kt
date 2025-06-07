// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework

import com.intellij.ide.plugins.ModuleDependencies
import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.ide.plugins.PluginContentDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.Compressor
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories


/** Constants from [com.intellij.platform.plugins.parser.impl.PluginXmlConst]
 * We can't access PluginXmlConst directly because it's in an implementation module */
private object PluginBuilderConsts {
  const val PLUGIN_IMPLEMENTATION_DETAIL_ATTR: String = "implementation-detail"
  const val PLUGIN_PACKAGE_ATTR: String = "package"
}


private val pluginIdCounter = AtomicInteger()

/**
 * use [com.intellij.platform.testFramework.plugins.plugin] instead
 */
@Deprecated("Use PluginSpec instead")
class PluginBuilder() {
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
  private var sinceBuild: String? = null
  private var version: String? = null
  private val pluginAliases = mutableListOf<String>()

  private val content = mutableListOf<PluginContentDescriptor.ModuleItem>()
  private val dependencies = mutableListOf<ModuleDependencies.ModuleReference>()
  private val pluginDependencies = mutableListOf<ModuleDependencies.PluginReference>()
  private val incompatibleWith = mutableListOf<ModuleDependencies.PluginReference>()

  private data class SubDescriptor(val filename: String, val builder: PluginBuilder)

  private val subDescriptors = ArrayList<SubDescriptor>()

  private var additionalXmlContent: String? = null

  // value = class fqn
  private var classes = mutableListOf<String>()
  // value = package fqn
  private var packages = mutableListOf<String>()

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

  fun module(
    moduleName: String, moduleDescriptor: PluginBuilder, loadingRule: ModuleLoadingRule = ModuleLoadingRule.OPTIONAL,
    moduleFile: String = "$moduleName.xml",
  ): PluginBuilder {
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

  fun sinceBuild(buildNumber: String): PluginBuilder {
    sinceBuild = buildNumber
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

  fun additionalXmlContent(@Language("XML") text: String?): PluginBuilder {
    additionalXmlContent = text
    return this
  }

  fun text(requireId: Boolean = true): String {
    return buildString {
      append("<idea-plugin")
      if (implementationDetail) {
        append(""" ${PluginBuilderConsts.PLUGIN_IMPLEMENTATION_DETAIL_ATTR}="true"""")
      }
      packagePrefix?.let {
        append(""" ${PluginBuilderConsts.PLUGIN_PACKAGE_ATTR}="$it"""")
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

      if (sinceBuild != null && untilBuild != null) {
        append("""<idea-version since-build="${sinceBuild}" until-build="${untilBuild}"/>""")
      }
      else if (sinceBuild != null) {
        append("""<idea-version since-build="${sinceBuild}"/>""")
      }
      else if (untilBuild != null) {
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

      if (additionalXmlContent != null) {
        append("\n")
        append(additionalXmlContent)
        append("\n")
      }

      append("</idea-plugin>")
    }
  }

  inline fun <reified T> includeClassFile(): PluginBuilder = includeClassFile(T::class.java.name)

  fun includeClassFile(classFqn: String): PluginBuilder {
    classes.add(classFqn)
    return this
  }

  inline fun <reified T> includePackageClassFiles(): PluginBuilder = includePackageClassFiles(T::class.java.packageName)

  fun includePackageClassFiles(packageFqn: String): PluginBuilder {
    packages.add(packageFqn)
    return this
  }

  fun build(path: Path): PluginBuilder {
    val allDescriptors = collectAllSubDescriptors(subDescriptors).toList()
    if (allDescriptors.any { it.builder.separateJar }) {
      val modulesDir = path.resolve("lib/modules")
      modulesDir.createDirectories()
      buildMainJar(path.resolve("lib/$id.jar"))
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

  fun buildMainJar(path: Path): PluginBuilder {
    path.createParentDirectories()
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
      for (classFqn in classes) {
        val url = this::class.java.classLoader.getResource(classFqn.replace('.', '/') + ".class")
                  ?: error("$classes not found")
        it.addFile(classFqn.replace('.', '/') + ".class", url.readBytes())
      }
      for (pkg in packages) {
        for (url in this::class.java.classLoader.getResources(pkg.replace('.', '/'))) {
          require(url.toString().endsWith('/'))
          val entries = url.readText().splitToSequence("\n").filter { !it.isBlank() }
          for (entry in entries) {
            if (entry.endsWith(".class")) {
              val bytes = this::class.java.classLoader.getResourceAsStream("${pkg.replace('.', '/')}/$entry")!!.readBytes()
              it.addFile(pkg.replace('.', '/') + "/$entry", bytes)
            }
          }
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

  private fun collectAllSubDescriptors(descriptors: List<SubDescriptor>): Sequence<SubDescriptor> {
    return descriptors.asSequence().flatMap { sequenceOf(it) + collectAllSubDescriptors(it.builder.subDescriptors) }
  }
}