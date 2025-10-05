// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.Version
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.AbstractGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import java.io.File
import java.util.function.Consumer
import kotlin.apply as applyKt

@ApiStatus.NonExtendable
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class TestGradleBuildScriptBuilder(
  gradleVersion: GradleVersion,
) : AbstractGradleBuildScriptBuilder<TestGradleBuildScriptBuilder>(gradleVersion, GradleDsl.GROOVY) {

  override fun apply(action: TestGradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  fun withTask(name: String) = withTask(name, null)
  fun withTask(name: String, type: String?) = withTask(name, type) {}
  fun withTask(name: String, configure: GradleScriptTreeBuilder.() -> Unit) = withTask(name, null, configure)
  fun withTask(name: String, type: String?, configure: GradleScriptTreeBuilder.() -> Unit) =
    withPostfix {
      val arguments = listOfNotNull(
        argument(name),
        type?.let { argument(code(it)) },
      )
      call("tasks.create", arguments, configure)
    }

  fun project(name: String, configure: Consumer<TestGradleBuildScriptBuilder>) = project(name) { configure.accept(this) }
  fun project(name: String, configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("project", name) {
        addElements(generateChildTree(configure))
      }
    }

  fun configure(expression: Expression, configure: Consumer<TestGradleBuildScriptBuilder>) = configure(expression) {
    configure.accept(this)
  }

  fun configure(expression: Expression, configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("configure", expression) {
        addElements(generateChildTree(configure))
      }
    }

  fun allprojects(configure: Consumer<TestGradleBuildScriptBuilder>) = allprojects { configure.accept(this) }
  fun allprojects(configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("allprojects") {
        addElements(generateChildTree(configure))
      }
    }

  fun subprojects(configure: TestGradleBuildScriptBuilder.() -> Unit) =
    withPrefix {
      call("subprojects") {
        addElements(generateChildTree(configure))
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
    addBuildScriptClasspath(call("file", jarFile.absolutePath))
    addBuildScriptClasspath("com.google.code.gson:gson:2.8.2")
    addBuildScriptClasspath("com.google.guava:guava:25.1-jre")
    applyPlugin("org.jetbrains.gradle.plugin.idea-ext")
  }

  override fun generate(): String {
    if (this !is TestGradleBuildScriptChildBuilder) {
      // Needed to identify how was created test script
      code("// $LICENCE")
    }
    return super.generate()
  }

  private fun generateChildTree(configure: TestGradleBuildScriptBuilder.() -> Unit): BlockElement {
    return TestGradleBuildScriptChildBuilder().also(configure).generateTree()
  }

  private inner class TestGradleBuildScriptChildBuilder : TestGradleBuildScriptBuilder(gradleVersion) {

    override fun withJavaPlugin() =
      applyPlugin("java")

    override fun withJavaLibraryPlugin() =
      applyPlugin("java-library")

    override fun withIdeaPlugin() =
      applyPlugin("idea")
  }

  companion object {
    const val LICENCE = "Build script is generated by TestGradleBuildScriptBuilder"

    const val IDEA_EXT_PLUGIN_VERSION = "1.2"

    @JvmStatic
    fun extPluginVersionIsAtLeast(version: String) =
      Version.parseVersion(IDEA_EXT_PLUGIN_VERSION)!! >= Version.parseVersion(version)!!
  }
}