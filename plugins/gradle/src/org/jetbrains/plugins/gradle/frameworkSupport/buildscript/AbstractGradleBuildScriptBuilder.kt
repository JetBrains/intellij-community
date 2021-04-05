// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.io.File

@Suppress("unused")
abstract class AbstractGradleBuildScriptBuilder<BSB : AbstractGradleBuildScriptBuilder<BSB>>(
  private val gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilderCore<BSB>(), GradleBuildScriptBuilder<BSB> {

  override fun addGroup(group: String) =
    withPrefix { assign("group", group) }

  override fun addVersion(version: String) =
    withPrefix { assign("version", version) }

  override fun addDependency(scope: String, dependency: String) =
    addDependency(scope, string(dependency))

  override fun addDependency(scope: String, dependency: Expression) =
    withDependency { call(scope, dependency) }

  override fun addImplementationDependency(dependency: String, sourceSet: String?) =
    addImplementationDependency(string(dependency), sourceSet)

  override fun addImplementationDependency(dependency: Expression, sourceSet: String?) =
    when (sourceSet) {
      null -> when (isSupportedImplementationScope(gradleVersion)) {
        true -> addDependency("implementation", dependency)
        else -> addDependency("compile", dependency)
      }
      else -> when (isSupportedImplementationScope(gradleVersion)) {
        true -> addDependency("${sourceSet}Implementation", dependency)
        else -> addDependency("${sourceSet}Compile", dependency)
      }
    }

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) =
    addRuntimeOnlyDependency(string(dependency), sourceSet)

  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?) =
    when (sourceSet) {
      null -> when (isSupportedRuntimeOnlyScope(gradleVersion)) {
        true -> addDependency("runtimeOnly", dependency)
        else -> addDependency("runtime", dependency)
      }
      else -> when (isSupportedRuntimeOnlyScope(gradleVersion)) {
        true -> addDependency("${sourceSet}RuntimeOnly", dependency)
        else -> addDependency("${sourceSet}Runtime", dependency)
      }
    }

  override fun addTestImplementationDependency(dependency: String) =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestImplementationDependency(dependency: Expression) =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: String) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: Expression) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addBuildScriptClasspath(dependency: String) =
    addBuildScriptClasspath(string(dependency))

  override fun addBuildScriptClasspath(dependency: Expression) =
    withBuildScriptDependency { call("classpath", dependency) }

  override fun withTask(name: String, type: String?, configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix {
      when (type) {
        null -> call("tasks.create", string(name), configure = configure)
        else -> call("tasks.create", string(name), code(type), configure = configure)
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

  override fun withJavaPlugin() = applyPlugin("java")

  override fun withIdeaPlugin() = applyPlugin("idea")

  override fun withKotlinPlugin(version: String) = apply {
    withBuildScriptPrefix { assign("ext.kotlin_version", version) }
    withBuildScriptMavenCentral()
    addBuildScriptClasspath("org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version")
    applyPlugin("kotlin")
  }

  override fun withGroovyPlugin() = apply {
    applyPlugin("groovy")
    withMavenCentral()
    addImplementationDependency("org.codehaus.groovy:groovy-all:3.0.5")
  }

  override fun withApplicationPlugin(mainClassName: String) = apply {
    applyPlugin("application")
    withPostfix { assign("mainClassName", mainClassName) }
  }

  override fun withJUnit() = when (isSupportedJUnit5(gradleVersion)) {
    true -> withJUnit5()
    else -> withJUnit4()
  }

  override fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency("junit:junit:4.12")
  }

  override fun withJUnit5() = apply {
    assert(isSupportedJUnit5(gradleVersion))
    withMavenCentral()
    addTestImplementationDependency("org.junit.jupiter:junit-jupiter-api:5.7.0")
    addTestRuntimeOnlyDependency("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    withPostfix {
      call("test") {
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
    addPlugin("org.jetbrains.gradle.plugin.idea-ext", IDEA_EXT_PLUGIN_VERSION)
  }

  override fun withLocalGradleIdeaExtPlugin(jarFile: File) = apply {
    withBuildScriptMavenCentral()
    addBuildScriptClasspath(call("files", FileUtil.toSystemIndependentName(jarFile.absolutePath)))
    addBuildScriptClasspath("com.google.code.gson:gson:2.8.2")
    addBuildScriptClasspath("com.google.guava:guava:25.1-jre")
    applyPlugin("org.jetbrains.gradle.plugin.idea-ext")
  }

  companion object {
    const val IDEA_EXT_PLUGIN_VERSION = "0.10"

    @JvmStatic
    fun extPluginVersionIsAtLeast(version: String): Boolean {
      return Version.parseVersion(IDEA_EXT_PLUGIN_VERSION)!! >= Version.parseVersion(version)!!
    }
  }
}