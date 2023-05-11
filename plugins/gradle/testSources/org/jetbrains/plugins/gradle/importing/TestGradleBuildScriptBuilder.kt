// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.Version
import com.intellij.testFramework.UsefulTestCase
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GroovyDslGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJavaLibraryPluginSupported
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isTaskConfigurationAvoidanceSupported
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.io.File
import java.util.function.Consumer
import kotlin.apply as applyKt

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class TestGradleBuildScriptBuilder(
  gradleVersion: GradleVersion
) : GroovyDslGradleBuildScriptBuilder<TestGradleBuildScriptBuilder>(gradleVersion) {

  override fun apply(action: TestGradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  fun withTask(name: String) = withTask(name, null)
  fun withTask(name: String, type: String?) = withTask(name, type, null)
  fun withTask(name: String, type: String?, dependsOn: String?) = withTask(name, type, dependsOn) {}
  fun withTask(name: String, configure: ScriptTreeBuilder.() -> Unit) = withTask(name, null, configure)
  fun withTask(name: String, type: String?, configure: ScriptTreeBuilder.() -> Unit) = withTask(name, type, null, configure)
  fun withTask(name: String, type: String?, dependsOn: String?, configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix {
      val arguments = listOfNotNull(
        argument(name),
        type?.let { argument(code(it)) },
      )
      call("tasks.create", arguments) {
        if (dependsOn != null) {
          call("dependsOn", dependsOn)
        }
        configure()
      }
    }

  fun registerTask(name: String, configure: ScriptTreeBuilder.() -> Unit) = apply {
    assert(isTaskConfigurationAvoidanceSupported(gradleVersion))
    withPostfix {
      call("tasks.register", name, configure = configure)
    }
  }

  // Note: These are Element building functions
  fun project(name: String) = call("project", name)
  fun project(name: String, configuration: String) = call("project", "path" to name, "configuration" to configuration)

  fun project(name: String, configure: Consumer<TestGradleBuildScriptBuilder>) = project(name) { configure.accept(this) }
  fun project(name: String, configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("project", name) {
        addElements(TestGradleBuildScriptChildBuilder().also(configure).generateTree())
      }
    }

  fun configure(expression: Expression, configure: Consumer<TestGradleBuildScriptBuilder>) = configure(expression) { configure.accept(this) }
  fun configure(expression: Expression, configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("configure", expression) {
        addElements(TestGradleBuildScriptChildBuilder().also(configure).generateTree())
      }
    }

  fun allprojects(configure: Consumer<TestGradleBuildScriptBuilder>) = allprojects { configure.accept(this) }
  fun allprojects(configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("allprojects") {
        addElements(TestGradleBuildScriptChildBuilder().also(configure).generateTree())
      }
    }

  fun subprojects(configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("subprojects") {
        addElements(TestGradleBuildScriptChildBuilder().also(configure).generateTree())
      }
    }

  fun subprojects(configure: Consumer<TestGradleBuildScriptBuilder>) = subprojects { configure.accept(this) }

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
    applyPlugin("org.jetbrains.gradle.plugin.idea-ext")
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
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      // IntelliJ internal maven repo is not available in local environment
      call("mavenCentral")
      return
    }

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

  override fun generate(): String {
    if (this !is TestGradleBuildScriptChildBuilder) {
      // Needed to identify how was created test script
      code("// $LICENCE")
    }
    return super.generate()
  }

  private inner class TestGradleBuildScriptChildBuilder : TestGradleBuildScriptBuilder(gradleVersion) {

    override fun withJavaPlugin() =
      applyPlugin("java")

    override fun withJavaLibraryPlugin() =
      if (isJavaLibraryPluginSupported(gradleVersion))
        applyPlugin("java-library")
      else
        applyPlugin("java")

    override fun withIdeaPlugin() =
      applyPlugin("idea")
  }

  companion object {
    const val LICENCE = "Build script is generated by TestGradleBuildScriptBuilder"

    const val IDEA_EXT_PLUGIN_VERSION = "1.1.3"

    @JvmStatic
    fun extPluginVersionIsAtLeast(version: String) =
      Version.parseVersion(IDEA_EXT_PLUGIN_VERSION)!! >= Version.parseVersion(version)!!
  }
}