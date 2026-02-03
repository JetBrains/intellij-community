// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.plugins

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.plugins.parser.impl.elements.xmlValue
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.directoryContent
import com.intellij.util.io.jarFile
import com.intellij.util.io.zipFile
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

open class PluginPackagingConfig {
  open val ContentModuleSpec.descriptorFilename: String get() {
    return "${moduleId.replace('/', '.')}.xml"
  }

  open val ContentModuleSpec.embedToPluginXml: Boolean get() {
    return false
  }

  open val ContentModuleSpec.jarFilename: String get() {
    return "${moduleId.replace('/', '.')}.jar"
  }

  open val ContentModuleSpec.packageToMainJar: Boolean get() {
    return spec.packagePrefix != null && !spec.isSeparateJar
  }

  val PluginSpec.isSingleJar: Boolean get() = content.all { it.packageToMainJar }
}

fun PluginSpec.buildXml(config: PluginPackagingConfig = PluginPackagingConfig()): String = with(config) {
  buildString {
    append("<idea-plugin")
    if (implementationDetail) append(""" implementation-detail="true"""")
    if (packagePrefix != null) append(""" package="$packagePrefix"""")
    if (isSeparateJar) append(""" separate-jar="true"""")
    if (moduleVisibility != ModuleVisibilityValue.PRIVATE) append(""" visibility="${moduleVisibility.name.lowercase()}"""")
    if (rootTagAttributes != null) append(" $rootTagAttributes")
    appendLine(">")
    if (id != null) appendLine("<id>$id</id>")
    if (name != null) appendLine("<name>$name</name>")
    if (sinceBuild != null || untilBuild != null || strictUntilBuild != null) {
      append("<idea-version")
      if (sinceBuild != null) append(""" since-build="${sinceBuild}"""")
      if (untilBuild != null) append(""" until-build="${untilBuild}"""")
      if (strictUntilBuild != null) append(""" strict-until-build="${strictUntilBuild}"""")
      appendLine("/>")
    }
    if (category != null) appendLine("<category>$category</category>")
    if (version != null) appendLine("<version>$version</version>")
    if (vendor != null) appendLine("<vendor>$vendor</vendor>")
    if (description != null) appendLine("<description>$description</description>")
    for (depends in pluginDependencies) {
      val optionalTag = if (depends.optional) " optional=\"true\"" else ""
      if (depends.configFile == null) {
        appendLine("""<depends$optionalTag>${depends.pluginId}</depends>""")
      } else {
        appendLine("""<depends$optionalTag config-file="${depends.configFile}">${depends.pluginId}</depends>""")
      }
    }
    if (moduleDependencies.isNotEmpty() || pluginMainModuleDependencies.isNotEmpty()) {
      appendLine("<dependencies>")
      for (module in moduleDependencies) {
        append("""<module name="${module.name}"""")
        if (module.namespace != null) append(""" namespace="${module.namespace}"""")
        appendLine(""" />""")
      }
      for (plugin in pluginMainModuleDependencies) {
        appendLine("""<plugin id="${plugin}" />""")
      }
      appendLine("</dependencies>")
    }
    for (alias in pluginAliases) {
      appendLine("""<module value="$alias"/>""")
    }
    for (plugin in incompatibleWith) {
      appendLine("""<incompatible-with>${plugin}</incompatible-with>""")
    }
    if (content.isNotEmpty()) {
      val attributes = if (namespace != null) """ namespace="$namespace"""" else ""
      appendLine("<content$attributes>")
      for (module in content) {
        val loadingAttribute = module.loadingRule.takeIf { it != ModuleLoadingRuleValue.OPTIONAL }?.let { "loading=\"${it.xmlValue}\" " }.orEmpty() +
                               module.requiredIfAvailable?.let { "required-if-available=\"$it\" " }.orEmpty()
        val tag = """module name="${module.moduleId}" $loadingAttribute"""
        if (module.embedToPluginXml) {
          appendLine("<$tag><![CDATA[${module.spec.buildXml(config)}]]></module>")
        } else {
          appendLine("<$tag/>")
        }
      }
      appendLine("</content>")
    }
    if (resourceBundle != null) appendLine("""<resource-bundle>$resourceBundle</resource-bundle>""")
    if (actions != null) appendLine("<actions>\n$actions\n</actions>")
    if (applicationListeners != null) appendLine("<applicationListeners>\n$applicationListeners\n</applicationListeners>")
    if (extensionPoints != null) appendLine("<extensionPoints>\n$extensionPoints\n</extensionPoints>")
    for (extensionBlock in extensions) {
      appendLine("""<extensions defaultExtensionNs="${extensionBlock.ns}">${extensionBlock.content}</extensions>""")
    }
    if (body != null) {
      appendLine()
      appendLine(body)
      appendLine()
    }
    append("</idea-plugin>")
  }
}

fun PluginSpec.buildDir(path: Path, config: PluginPackagingConfig = PluginPackagingConfig()): Unit = with(config) {
  directoryContent {
    if (isSingleJar) {
      buildMainDir(this, config)
    } else {
      buildDir(this@directoryContent, config)
    }
  }.generate(path)
}

private fun PluginSpec.buildDir(
  builder: DirectoryContentBuilder,
  config: PluginPackagingConfig,
) = with(config) {
  builder.dir("lib") {
    zip("${id!!}.jar") {
      buildMainDir(this, config)
    }
    dir("modules") {
      for (module in content) {
        if (module.packageToMainJar) continue
        zip(module.jarFilename) {
          module.buildContentDir(this, config)
        }
      }
    }
  }
}

fun PluginSpec.buildDistribution(dir: Path, config: PluginPackagingConfig = PluginPackagingConfig()): Path = with(config) {
  val archiveName = name ?: id ?: error("neither name or id specified")
  if (isSingleJar) {
    val path = dir.resolve("$archiveName.jar")
    buildMainJar(path, config)
    return path
  } else {
    val path = dir.resolve("$archiveName.zip")
    buildZip(path, config)
    return path
  }
}

fun PluginSpec.buildMainJar(path: Path, config: PluginPackagingConfig = PluginPackagingConfig()) {
  jarFile {
    buildMainDir(this, config)
  }.generate(path)
}

fun PluginSpec.buildZip(path: Path, config: PluginPackagingConfig = PluginPackagingConfig()) {
  zipFile {
    val rootDir = name ?: id ?: error("neither name or id specified")
    dir(rootDir) {
      buildDir(this, config)
    }
  }.generate(path)
}

private fun PluginSpec.buildMainDir(dir: DirectoryContentBuilder, config: PluginPackagingConfig) = with(config) {
  dir.dirsFile("META-INF/plugin.xml", buildXml(config))
  for (dep in sequencePluginDependenciesRecursive()) {
    if (dep.configFile != null) {
      dir.dirsFile("META-INF/${dep.configFile}", dep.spec!!.buildXml(config))
    }
  }
  buildClasses(dir)
  for (module in content) {
    if (!module.packageToMainJar) continue
    module.buildContentDir(dir, config)
  }
}

private fun ContentModuleSpec.buildContentDir(dir: DirectoryContentBuilder, config: PluginPackagingConfig) = with(config) {
  dir.dirsFile(descriptorFilename, spec.buildXml(config))
  spec.buildClasses(dir)
}

private fun PluginSpec.buildClasses(dir: DirectoryContentBuilder) {
  for ((classFqn, classLoader) in classFiles) {
    val url = (classLoader ?: this::class.java.classLoader).getResource(classFqn.replace('.', '/') + ".class")
              ?: error("$classFqn not found")
    dir.dirsFile(classFqn.replace('.', '/') + ".class", url.readBytes())
  }
  for ((pkg, classLoader) in packageClassFiles) {
    for (url in (classLoader ?: this::class.java.classLoader).getResources(pkg.replace('.', '/'))) {
      require(url.toString().endsWith('/')) { url }
      val packageEntries: List<String> = if (url.protocol.contains("jar")) {
        FileSystems.newFileSystem(url.toURI(), mutableMapOf<String, Any>()).use { jarFs ->
          val pkgPath = jarFs.getPath(pkg.replace('.', '/'))
          pkgPath.listDirectoryEntries().map { it.name }
        }
      } else {
        url.readText().splitToSequence("\n").filter { !it.isBlank() }.toList()
      }
      for (entry in packageEntries) {
        if (entry.endsWith(".class")) {
          val bytes = this::class.java.classLoader.getResource("${pkg.replace('.', '/')}/$entry")!!.readBytes()
          dir.dirsFile(pkg.replace('.', '/') + "/$entry", bytes)
        }
      }
    }
  }
}

private fun DirectoryContentBuilder.dirsFile(path: String, content: ByteArray) {
  val slash = path.lastIndexOf('/')
  if (slash == -1) {
    file(path, content)
  } else {
    dirs(path.take(slash)) {
      file(path.substring(slash + 1), content)
    }
  }
}

private fun DirectoryContentBuilder.dirsFile(path: String, content: String) = dirsFile(path, content.toByteArray())

private tailrec fun DirectoryContentBuilder.dirs(path: String, body: DirectoryContentBuilder.() -> Unit) {
  val slash = path.lastIndexOf('/')
  if (slash == -1) {
    dir(path) {
      body()
    }
  } else {
    dirs(path.take(slash)) {
      dir(path.substring(slash + 1)) {
        body()
      }
    }
  }
}

private fun PluginSpec.sequencePluginDependenciesRecursive(): Sequence<DependsSpec> {
  return sequence {
    for (dep in pluginDependencies) {
      yield(dep)
      if (dep.spec != null) {
        yieldAll(dep.spec.sequencePluginDependenciesRecursive())
      }
    }
  }
}