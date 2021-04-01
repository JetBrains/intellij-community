// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import java.io.File

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class AbstractGradleBuildScriptBuilder<SB : ScriptBuilder<SB>, BSB : AbstractGradleBuildScriptBuilder<SB, BSB>>(
  private val gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilderCore<SB, BSB>(), GradleBuildScriptBuilder<SB, BSB> {

  override fun group(group: String) =
    withPrefix { assign("group", group) }

  override fun version(version: String) =
    withPrefix { assign("version", version) }

  override fun addImplementationDependency(dependency: String, sourceSet: String?) =
    when (sourceSet) {
      null -> when (isSupportedImplementationScope(gradleVersion)) {
        true -> withDependency { call("implementation", dependency) }
        else -> withDependency { call("compile", dependency) }
      }
      else -> when (isSupportedImplementationScope(gradleVersion)) {
        true -> withDependency { call("${sourceSet}Implementation", dependency) }
        else -> withDependency { call("${sourceSet}Compile", dependency) }
      }
    }

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) =
    when (sourceSet) {
      null -> when (isSupportedRuntimeOnlyScope(gradleVersion)) {
        true -> withDependency { call("runtimeOnly", dependency) }
        else -> withDependency { call("runtime", dependency) }
      }
      else -> when (isSupportedRuntimeOnlyScope(gradleVersion)) {
        true -> withDependency { call("${sourceSet}RuntimeOnly", dependency) }
        else -> withDependency { call("${sourceSet}Runtime", dependency) }
      }
    }

  override fun addTestImplementationDependency(dependency: String) =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: String) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addBuildScriptClasspath(dependency: String) =
    withBuildScriptDependency { call("classpath", dependency) }

  override fun withTask(name: String, type: String?, configure: SB.() -> Unit) =
    withPostfix {
      when (type) {
        null -> call("tasks.create", str(name), configure = configure)
        else -> call("tasks.create", str(name), type, configure = configure)
      }
    }

  override fun withBuildScriptMavenCentral(useOldStyleMetadata: Boolean) =
    withBuildScriptRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  override fun withMavenCentral(useOldStyleMetadata: Boolean) =
    withRepository {
      mavenCentralRepository(useOldStyleMetadata)
    }

  private fun SB.mavenCentralRepository(useOldStyleMetadata: Boolean = false) {
    block("maven") {
      call("url", str("https://repo.labs.intellij.net/repo1"))
      if (useOldStyleMetadata) {
        block("metadataSources") {
          call("mavenPom")
          call("artifact")
        }
      }
    }
  }

  override fun withJavaPlugin() = applyPlugin(str("java"))

  override fun withIdeaPlugin() = applyPlugin(str("idea"))

  override fun withKotlinPlugin(version: String) = apply {
    withBuildScriptPrefix { assign("ext.kotlin_version", str(version)) }
    withBuildScriptMavenCentral()
    addBuildScriptClasspath(str("org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version"))
    applyPlugin(str("kotlin"))
  }

  override fun withGroovyPlugin() = apply {
    applyPlugin(str("groovy"))
    withMavenCentral()
    addImplementationDependency(str("org.codehaus.groovy:groovy-all:3.0.5"))
  }

  override fun withApplicationPlugin(mainClassName: String) = apply {
    applyPlugin(str("application"))
    withPostfix { assign("mainClassName", str(mainClassName)) }
  }

  override fun withJUnit() = when (isSupportedJUnit5(gradleVersion)) {
    true -> withJUnit5()
    else -> withJUnit4()
  }

  override fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency(str("junit:junit:4.12"))
  }

  override fun withJUnit5() = apply {
    assert(isSupportedJUnit5(gradleVersion))
    withMavenCentral()
    addTestImplementationDependency(str("org.junit.jupiter:junit-jupiter-api:5.7.0"))
    addTestRuntimeOnlyDependency(str("org.junit.jupiter:junit-jupiter-engine:5.7.0"))
    withPostfix {
      block("test") {
        call("useJUnitPlatform")
      }
    }
  }

  override fun withGradleIdeaExtPluginIfCan() = apply {
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

  override fun withGradleIdeaExtPlugin() = apply {
    addPlugin(str("org.jetbrains.gradle.plugin.idea-ext"), str(IDEA_EXT_PLUGIN_VERSION))
  }

  override fun withLocalGradleIdeaExtPlugin(jarFile: File) = apply {
    withBuildScriptMavenCentral()
    addBuildScriptClasspath("files(${str(FileUtil.toSystemIndependentName(jarFile.absolutePath))})")
    addBuildScriptClasspath(str("com.google.code.gson:gson:2.8.2"))
    addBuildScriptClasspath(str("com.google.guava:guava:25.1-jre"))
    applyPlugin(str("org.jetbrains.gradle.plugin.idea-ext"))
  }

  companion object {
    const val IDEA_EXT_PLUGIN_VERSION = "0.10"

    @JvmStatic
    fun extPluginVersionIsAtLeast(version: String): Boolean {
      return Version.parseVersion(IDEA_EXT_PLUGIN_VERSION)!! >= Version.parseVersion(version)!!
    }
  }
}