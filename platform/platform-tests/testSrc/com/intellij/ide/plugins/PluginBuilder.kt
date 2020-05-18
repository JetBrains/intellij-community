// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.util.io.Compressor
import com.intellij.util.io.write
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class PluginBuilder {
  private data class ExtensionBlock(val ns: String, val text: String)
  private data class DependsTag(val pluginId: String, val configFile: String?)

  var id: String = UUID.randomUUID().toString()

  private var implementationDetail = false
  private var name: String? = null
  private var description: String? = null
  private val dependsTags = mutableListOf<DependsTag>()
  private var applicationListeners: String? = null
  private var actions: String? = null
  private val extensions = mutableListOf<ExtensionBlock>()
  private var extensionPoints: String? = null
  private var untilBuild: String? = null
  private var version: String? = null

  init {
    depends("com.intellij.modules.lang")
  }

  fun id(id: String): PluginBuilder {
    this.id = id
    return this
  }

  fun randomId(idPrefix: String): PluginBuilder {
    this.id = "$idPrefix${UUID.randomUUID()}"
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

  fun depends(pluginId: String, configFile: String? = null): PluginBuilder {
    dependsTags.add(DependsTag(pluginId, configFile))
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

  fun extensionPoints(text: String): PluginBuilder {
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
        append(""" implementation-detail="true"""")
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
      append("</idea-plugin>")
    }
  }

  fun build(path: Path) {
    val pluginXmlPath = path.resolve("META-INF/plugin.xml")
    pluginXmlPath.write(text())
  }

  fun buildJar(path: Path): PluginBuilder {
    buildJarToStream(Files.newOutputStream(path))
    return this
  }

  private fun buildJarToStream(outputStream: OutputStream) {
    Compressor.Zip(outputStream).use {
      it.addFile("META-INF/plugin.xml", text().toByteArray())
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
