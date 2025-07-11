// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.plugins

import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.directoryContent
import com.intellij.util.io.jarFile
import com.intellij.util.io.zipFile
import java.nio.file.Path

open class PluginPackagingConfig {
  open val ContentModuleSpec.descriptorFilename: String get() {
    return "${moduleName.replace('/', '.')}.xml"
  }

  open val ContentModuleSpec.embedToPluginXml: Boolean get() {
    return false
  }

  open val ContentModuleSpec.jarFilename: String get() {
    return "${moduleName.replace('/', '.')}.jar"
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
    if (rootTagAttributes != null) append(" $rootTagAttributes")
    appendLine(">")
    if (id != null) appendLine("<id>$id</id>")
    if (name != null) appendLine("<name>$name</name>")
    when {
      sinceBuild != null && untilBuild != null -> appendLine("""<idea-version since-build="${sinceBuild}" until-build="${untilBuild}"/>""")
      sinceBuild != null -> appendLine("""<idea-version since-build="${sinceBuild}"/>""")
      untilBuild != null -> appendLine("""<idea-version until-build="${untilBuild}"/>""")
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
        appendLine("""<module name="${module}" />""")
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
      appendLine("<content>")
      for (module in content) {
        val loadingAttribute = when (module.loadingRule) {
          ModuleLoadingRule.OPTIONAL -> ""
          ModuleLoadingRule.REQUIRED -> "loading=\"required\" "
          ModuleLoadingRule.EMBEDDED -> "loading=\"embedded\" "
          ModuleLoadingRule.ON_DEMAND -> "loading=\"on-demand\" "
        }
        val tag = """module name="${module.moduleName}" $loadingAttribute"""
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
  for (classFqn in classFiles) {
    val url = this::class.java.classLoader.getResource(classFqn.replace('.', '/') + ".class")
              ?: error("$classFqn not found")
    dir.dirsFile(classFqn.replace('.', '/') + ".class", url.readBytes())
  }
  for (pkg in packageClassFiles) {
    for (url in this::class.java.classLoader.getResources(pkg.replace('.', '/'))) {
      require(url.toString().endsWith('/')) { url }
      val entries = url.readText().splitToSequence("\n").filter { !it.isBlank() }
      for (entry in entries) {
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