// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@ApiStatus.NonExtendable
interface GradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>> : GradleBuildScriptBuilderCore<BSB> {

  fun addGroup(group: String): BSB
  fun addVersion(version: String): BSB

  fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit): BSB

  fun addDependency(scope: String, dependency: String) = addDependency(scope, dependency, null)
  fun addDependency(scope: String, dependency: Expression) = addDependency(scope, dependency, null)
  fun addDependency(scope: String, dependency: String, sourceSet: String?): BSB
  fun addDependency(scope: String, dependency: Expression, sourceSet: String?): BSB

  fun addApiDependency(dependency: String) = addApiDependency(dependency, null)
  fun addApiDependency(dependency: Expression) = addApiDependency(dependency, null)
  fun addApiDependency(dependency: String, sourceSet: String?): BSB
  fun addApiDependency(dependency: Expression, sourceSet: String?): BSB

  fun addCompileOnlyDependency(dependency: String) = addCompileOnlyDependency(dependency, null)
  fun addCompileOnlyDependency(dependency: Expression) = addCompileOnlyDependency(dependency, null)
  fun addCompileOnlyDependency(dependency: String, sourceSet: String?): BSB
  fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?): BSB

  fun addImplementationDependency(dependency: String) = addImplementationDependency(dependency, null)
  fun addImplementationDependency(dependency: Expression) = addImplementationDependency(dependency, null)
  fun addImplementationDependency(dependency: String, sourceSet: String?): BSB
  fun addImplementationDependency(dependency: Expression, sourceSet: String?): BSB

  fun addRuntimeOnlyDependency(dependency: String) = addRuntimeOnlyDependency(dependency, null)
  fun addRuntimeOnlyDependency(dependency: Expression) = addRuntimeOnlyDependency(dependency, null)
  fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?): BSB
  fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?): BSB

  fun addTestImplementationDependency(dependency: String): BSB
  fun addTestImplementationDependency(dependency: Expression): BSB

  fun addTestRuntimeOnlyDependency(dependency: String): BSB
  fun addTestRuntimeOnlyDependency(dependency: Expression): BSB

  fun addBuildScriptClasspath(dependency: String): BSB
  fun addBuildScriptClasspath(dependency: Expression): BSB

  fun withMavenCentral(): BSB
  fun withBuildScriptMavenCentral(): BSB

  fun applyPlugin(plugin: String): BSB
  fun applyPluginFrom(path: String): BSB

  fun withPlugin(id: String) = withPlugin(id, null)
  fun withPlugin(id: String, version: String?): BSB

  fun withJavaPlugin(): BSB
  fun withJavaLibraryPlugin(): BSB
  fun withIdeaPlugin(): BSB
  fun withKotlinJvmPlugin(): BSB

  /**
   * Adds the Kotlin JVM plugin using the [version], or omitting the version call if [version] is null.
   */
  fun withKotlinJvmPlugin(version: String?): BSB
  fun withKotlinJsPlugin(): BSB
  fun withKotlinMultiplatformPlugin(): BSB
  fun withKotlinJvmToolchain(jvmTarget: Int): BSB
  fun withKotlinDsl(): BSB
  fun withGroovyPlugin(): BSB
  fun withGroovyPlugin(version: String): BSB
  fun withApplicationPlugin(
    mainClass: String? = null,
    mainModule: String? = null,
    executableDir: String? = null,
    defaultJvmArgs: List<String>? = null
  ): BSB

  fun withKotlinTest(): BSB
  fun withJUnit(): BSB
  fun withJUnit4(): BSB
  fun withJUnit5(): BSB
  fun targetCompatibility(level: String): BSB
  fun sourceCompatibility(level: String): BSB

  // Note: These are Element building functions
  fun project(name: String): Expression
  fun project(name: String, configuration: String): Expression

  fun ScriptTreeBuilder.mavenRepository(url: String): ScriptTreeBuilder
  fun ScriptTreeBuilder.mavenCentral(): ScriptTreeBuilder

  companion object {

    @JvmStatic
    fun create(gradleVersion: GradleVersion, useKotlinDsl: Boolean): GradleBuildScriptBuilder<*> {
      return when (useKotlinDsl) {
        true -> KotlinDslGradleBuildScriptBuilder.Impl(gradleVersion)
        else -> GroovyDslGradleBuildScriptBuilder.Impl(gradleVersion)
      }
    }
  }
}