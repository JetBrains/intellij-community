// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.importing.GradleSettingsImportingTestCase.IDEA_EXT_PLUGIN_VERSION
import org.jetbrains.plugins.gradle.importing.GroovyBuilder.Companion.groovy
import java.io.File
import java.util.function.Consumer

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GradleBuildScriptBuilder() {

  private val imports = GroovyBuilder()
  private val buildScriptPrefixes = GroovyBuilder()
  private val buildScriptDependencies = GroovyBuilder()
  private val buildScriptRepositories = GroovyBuilder()
  private val buildScriptPostfixes = GroovyBuilder()
  private val plugins = GroovyBuilder()
  private val applicablePlugins = GroovyBuilder()
  private val prefixes = GroovyBuilder()
  private val dependencies = GroovyBuilder()
  private val repositories = GroovyBuilder()
  private val postfixes = GroovyBuilder()

  constructor(configure: GradleBuildScriptBuilder.() -> Unit) : this() {
    this.configure()
  }

  private fun apply(builder: GroovyBuilder, configure: GroovyBuilder.() -> Unit) = apply { builder.configure() }
  private fun applyIfNotContains(builder: GroovyBuilder, configure: GroovyBuilder.() -> Unit) = apply {
    val childBuilder = GroovyBuilder(configure)
    if (childBuilder !in builder) {
      builder.join(childBuilder)
    }
  }

  /**
   * ...
   * import [import]
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addImport(import: String) = applyIfNotContains(imports) { code("import $import") }

  /**
   * buildscript {
   *   ...
   *   [prefix]
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   * }
   */
  fun addBuildScriptPrefix(vararg prefix: String) = withBuildScriptPrefix { code(*prefix) }
  fun withBuildScriptPrefix(configure: GroovyBuilder.() -> Unit) = apply(buildScriptPrefixes, configure)
  fun withBuildScriptPrefix(configure: Consumer<GroovyBuilder>) = withBuildScriptPrefix(configure::accept)

  /**
   * buildscript {
   *   dependencies {
   *     ...
   *     [dependency]
   *   }
   * }
   */
  fun addBuildScriptDependency(dependency: String) = withBuildScriptDependency { code(dependency) }
  fun withBuildScriptDependency(configure: GroovyBuilder.() -> Unit) = applyIfNotContains(buildScriptDependencies, configure)
  fun withBuildScriptDependency(configure: Consumer<GroovyBuilder>) = withBuildScriptDependency(configure::accept)

  /**
   * buildscript {
   *   repositories {
   *     ...
   *     [repository]
   *   }
   * }
   */
  fun addBuildScriptRepository(repository: String) = withBuildScriptRepository { code(repository) }
  fun withBuildScriptRepository(configure: GroovyBuilder.() -> Unit) = applyIfNotContains(buildScriptRepositories, configure)
  fun withBuildScriptRepository(configure: Consumer<GroovyBuilder>) = withBuildScriptRepository(configure::accept)

  /**
   * buildscript {
   *   ...
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   *   [postfix]
   * }
   */
  fun addBuildScriptPostfix(vararg postfix: String) = withBuildScriptPostfix { code(*postfix) }
  fun withBuildScriptPostfix(configure: GroovyBuilder.() -> Unit) = apply(buildScriptPostfixes, configure)
  fun withBuildScriptPostfix(configure: Consumer<GroovyBuilder>) = withBuildScriptPostfix(configure::accept)

  /**
   * plugins {
   *   ...
   *   [plugin]
   * }
   */
  fun addPlugin(plugin: String) = applyIfNotContains(plugins) { code(plugin) }

  /**
   * apply plugin: [plugin]
   */
  fun applyPlugin(plugin: String) = applyIfNotContains(applicablePlugins) { call("apply", "plugin: $plugin") }

  /**
   * buildscript { ... }
   * ...
   * [prefix]
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addPrefix(vararg prefix: String) = withPrefix { code(*prefix) }
  fun withPrefix(configure: GroovyBuilder.() -> Unit) = apply(prefixes, configure)
  fun withPrefix(configure: Consumer<GroovyBuilder>) = withPrefix(configure::accept)

  /**
   * dependencies {
   *   ...
   *   [dependency]
   * }
   */
  fun addDependency(dependency: String) = withDependency { code(dependency) }
  fun withDependency(configure: GroovyBuilder.() -> Unit) = applyIfNotContains(dependencies, configure)
  fun withDependency(configure: Consumer<GroovyBuilder>) = withDependency(configure::accept)

  /**
   * repositories {
   *   ...
   *   [repository]
   * }
   */
  fun addRepository(repository: String) = withRepository { code(repository) }
  fun withRepository(configure: GroovyBuilder.() -> Unit) = applyIfNotContains(repositories, configure)
  fun withRepository(configure: Consumer<GroovyBuilder>) = withRepository(configure::accept)

  /**
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   * [postfix]
   */
  fun addPostfix(vararg postfix: String) = withPostfix { code(*postfix) }
  fun withPostfix(configure: GroovyBuilder.() -> Unit) = apply(postfixes, configure)
  fun withPostfix(configure: Consumer<GroovyBuilder>) = withPostfix(configure::accept)


  fun group(group: String) =
    addPrefix("group = '$group'")

  fun version(version: String) =
    addPrefix("version = '$version'")

  fun addImplementationDependency(dependency: String, sourceSet: String? = null) =
    when (sourceSet) {
      null -> addDependency("implementation $dependency")
      else -> addDependency("${sourceSet}Implementation $dependency")
    }

  fun addRuntimeOnlyDependency(dependency: String, sourceSet: String? = null) =
    when (sourceSet) {
      null -> addDependency("runtimeOnly $dependency")
      else -> addDependency("${sourceSet}RuntimeOnly $dependency")
    }

  fun addTestImplementationDependency(dependency: String) =
    addImplementationDependency(dependency, sourceSet = "test")

  fun addTestRuntimeOnlyDependency(dependency: String) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  fun addBuildScriptClasspath(dependency: String) =
    addBuildScriptDependency("classpath $dependency")

  fun withTask(name: String, type: String? = null, configure: GroovyBuilder.() -> Unit = {}) =
    withPostfix {
      when (type) {
        null -> call("tasks.create", "'$name'", configure = configure)
        else -> call("tasks.create", "'$name'", type, configure = configure)
      }
    }

  fun withBuildScriptMavenCentral(useOldStyleMetadata: Boolean = false) =
    withBuildScriptRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  fun withMavenCentral(useOldStyleMetadata: Boolean = false) =
    withRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  private fun GroovyBuilder.mavenCentralRepository(useOldStyleMetadata: Boolean = false) {
    block("maven") {
      call("url", "'https://repo.labs.intellij.net/repo1'")
      if (useOldStyleMetadata) {
        block("metadataSources") {
          call("mavenPom")
          call("artifact")
        }
      }
    }
  }

  fun withJavaPlugin() = applyPlugin("'java'")

  fun withIdeaPlugin() = applyPlugin("'idea'")

  fun withKotlinPlugin(version: String) = apply {
    addBuildScriptPrefix("ext.kotlin_version = '$version'")
    withBuildScriptMavenCentral()
    addBuildScriptClasspath(""""org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version"""")
    applyPlugin("'kotlin'")
  }

  fun withGroovyPlugin() = apply {
    applyPlugin("'groovy'")
    withMavenCentral()
    addImplementationDependency("'org.codehaus.groovy:groovy-all:3.0.5'")
  }

  fun withApplicationPlugin(mainClassName: String) = apply {
    applyPlugin("'application'")
    addPostfix("mainClassName = '$mainClassName'")
  }

  fun withJUnit() = withJUnit5()

  fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency("'junit:junit:4.12'")
  }

  fun withJUnit5() = apply {
    withMavenCentral()
    addTestImplementationDependency("'org.junit.jupiter:junit-jupiter-api:5.7.0'")
    addTestRuntimeOnlyDependency("'org.junit.jupiter:junit-jupiter-engine:5.7.0'")
    withPostfix {
      call("tasks.withType", "Test") {
        call("useJUnitPlatform")
      }
    }
  }

  fun withGradleIdeaExtPluginIfCan() = apply {
    val localDirWithJar = System.getenv("GRADLE_IDEA_EXT_PLUGIN_DIR")?.let(::File)
    if (localDirWithJar == null) {
      withGradleIdeaExtPlugin()
      return@apply
    }
    if (!localDirWithJar.exists()) throw RuntimeException("Directory $localDirWithJar not found")
    if (!localDirWithJar.isDirectory) throw RuntimeException("File $localDirWithJar is not directory")
    val template = "gradle-idea-ext-.+-SNAPSHOT\\.jar".toRegex()
    val jarFile = localDirWithJar.listFiles()?.find { it.name.matches(template) }
    if (jarFile == null) throw RuntimeException("Jar with gradle-idea-ext plugin not found")
    if (!jarFile.isFile) throw RuntimeException("Invalid jar file $jarFile")
    withLocalGradleIdeaExtPlugin(jarFile)
  }

  fun withGradleIdeaExtPlugin() = apply {
    addPlugin("id 'org.jetbrains.gradle.plugin.idea-ext' version '$IDEA_EXT_PLUGIN_VERSION'")
  }

  fun withLocalGradleIdeaExtPlugin(jarFile: File) = apply {
    withBuildScriptMavenCentral()
    addBuildScriptClasspath("files('${FileUtil.toSystemIndependentName(jarFile.absolutePath)}')")
    addBuildScriptClasspath("'com.google.code.gson:gson:2.8.2'")
    addBuildScriptClasspath("'com.google.guava:guava:25.1-jre'")
    applyPlugin("'org.jetbrains.gradle.plugin.idea-ext'")
  }

  /**
   * @return content for build.gradle
   */
  fun generate() = groovy {
    join(imports)
    blockIfNotEmpty("buildscript") {
      join(buildScriptPrefixes)
      blockIfNotEmpty("repositories", buildScriptRepositories)
      blockIfNotEmpty("dependencies", buildScriptDependencies)
      join(buildScriptPostfixes)
    }
    blockIfNotEmpty("plugins", plugins)
    join(applicablePlugins)
    join(prefixes)
    blockIfNotEmpty("repositories", repositories)
    blockIfNotEmpty("dependencies", dependencies)
    join(postfixes)
  }

  companion object {
    fun buildscript(configure: GradleBuildScriptBuilder.() -> Unit) = GradleBuildScriptBuilder(configure).generate()
  }
}