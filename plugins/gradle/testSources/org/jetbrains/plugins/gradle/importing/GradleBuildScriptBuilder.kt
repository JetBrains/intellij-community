// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.Version
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.AbstractGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.io.File
import java.util.function.Consumer
import kotlin.apply as applyKt

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GradleBuildScriptBuilder(gradleVersion: GradleVersion) : AbstractGradleBuildScriptBuilder<GradleBuildScriptBuilder>(gradleVersion) {
  override val scriptBuilder = GroovyScriptBuilder()

  override fun apply(action: GradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  fun applyPlugin(plugin: String) =
    withPrefix { call("apply", argument("plugin", code(plugin))) }

  fun withTask(name: String, type: String? = null, configure: ScriptTreeBuilder.() -> Unit = {}) =
    withPostfix {
      when (type) {
        null -> call("tasks.create", string(name), configure = configure)
        else -> call("tasks.create", string(name), code(type), configure = configure)
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

  fun withGradleIdeaExtPlugin() =
    withPlugin("org.jetbrains.gradle.plugin.idea-ext", IDEA_EXT_PLUGIN_VERSION)

  fun withLocalGradleIdeaExtPlugin(jarFile: File) = apply {
    withBuildScriptMavenCentral()
    addBuildScriptClasspath(jarFile)
    addBuildScriptClasspath("com.google.code.gson:gson:2.8.2")
    addBuildScriptClasspath("com.google.guava:guava:25.1-jre")
    applyPlugin("'org.jetbrains.gradle.plugin.idea-ext'")
  }

  override fun withBuildScriptMavenCentral() =
    withBuildScriptMavenCentral(false)

  override fun withMavenCentral() =
    withMavenCentral(false)

  fun withBuildScriptMavenCentral(useOldStyleMetadata: Boolean) =
    withBuildScriptRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  fun withMavenCentral(useOldStyleMetadata: Boolean) =
    withRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  private fun ScriptTreeBuilder.mavenCentralRepository(useOldStyleMetadata: Boolean = false) {
    call("maven") {
      call("url", "https://repo.labs.intellij.net/repo1")
      if (useOldStyleMetadata) {
        call("metadataSources") {
          call("mavenPom")
          call("artifact")
        }
      }
    }
  }

  companion object {
    const val IDEA_EXT_PLUGIN_VERSION = "0.10"

    @JvmStatic
    fun extPluginVersionIsAtLeast(version: String) =
      Version.parseVersion(IDEA_EXT_PLUGIN_VERSION)!! >= Version.parseVersion(version)!!

    @JvmStatic
    fun buildscript(gradleVersion: GradleVersion, configure: Consumer<GradleBuildScriptBuilder>) =
      buildscript(gradleVersion, configure::accept)

    fun buildscript(gradleVersion: GradleVersion, configure: GradleBuildScriptBuilder.() -> Unit) =
      GradleBuildScriptBuilder(gradleVersion).apply(configure).generate()

    @JvmStatic
    fun GradleImportingTestCase.buildscript(configure: Consumer<GradleBuildScriptBuilder>) =
      buildscript(configure::accept)

    fun GradleImportingTestCase.buildscript(configure: GradleBuildScriptBuilder.() -> Unit) =
      createBuildScriptBuilder().apply(configure).generate()
  }
}