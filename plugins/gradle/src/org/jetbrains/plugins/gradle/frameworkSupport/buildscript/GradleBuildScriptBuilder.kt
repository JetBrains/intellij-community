// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Consumer

@ApiStatus.NonExtendable
interface GradleBuildScriptBuilder<Self : GradleBuildScriptBuilder<Self>>
  : GradleBuildScriptBuilderCore<Self> {

  fun addGroup(group: String): Self
  fun addVersion(version: String): Self

  fun registerTask(name: String, type: String?, configure: Consumer<ScriptTreeBuilder>): Self = registerTask(name, type) { configure.accept(this) }
  fun registerTask(name: String, type: String? = null, configure: ScriptTreeBuilder.() -> Unit = {}): Self

  fun configureTask(name: String, type: String, configure: Consumer<ScriptTreeBuilder>): Self = configureTask(name, type) { configure.accept(this) }
  fun configureTask(name: String, type: String, configure: ScriptTreeBuilder.() -> Unit): Self
  fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit): Self

  fun addDependency(scope: String, dependency: String): Self = addDependency(scope, dependency, null)
  fun addDependency(scope: String, dependency: Expression): Self = addDependency(scope, dependency, null)
  fun addDependency(scope: String, dependency: String, sourceSet: String?): Self
  fun addDependency(scope: String, dependency: Expression, sourceSet: String?): Self

  fun addApiDependency(dependency: String): Self = addApiDependency(dependency, null)
  fun addApiDependency(dependency: Expression): Self = addApiDependency(dependency, null)
  fun addApiDependency(dependency: String, sourceSet: String?): Self
  fun addApiDependency(dependency: Expression, sourceSet: String?): Self

  fun addCompileOnlyDependency(dependency: String): Self = addCompileOnlyDependency(dependency, null)
  fun addCompileOnlyDependency(dependency: Expression): Self = addCompileOnlyDependency(dependency, null)
  fun addCompileOnlyDependency(dependency: String, sourceSet: String?): Self
  fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?): Self

  fun addImplementationDependency(dependency: String): Self = addImplementationDependency(dependency, null)
  fun addImplementationDependency(dependency: Expression): Self = addImplementationDependency(dependency, null)
  fun addImplementationDependency(dependency: String, sourceSet: String?): Self
  fun addImplementationDependency(dependency: Expression, sourceSet: String?): Self

  fun addRuntimeOnlyDependency(dependency: String): Self = addRuntimeOnlyDependency(dependency, null)
  fun addRuntimeOnlyDependency(dependency: Expression): Self = addRuntimeOnlyDependency(dependency, null)
  fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?): Self
  fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?): Self

  fun addTestImplementationDependency(dependency: String): Self
  fun addTestImplementationDependency(dependency: Expression): Self

  fun addTestRuntimeOnlyDependency(dependency: String): Self
  fun addTestRuntimeOnlyDependency(dependency: Expression): Self

  fun addBuildScriptClasspath(dependency: String): Self
  fun addBuildScriptClasspath(dependency: Expression): Self

  fun withMavenCentral(): Self
  fun withBuildScriptMavenCentral(): Self

  fun applyPlugin(plugin: String): Self
  fun applyPluginFrom(path: String): Self

  fun withPlugin(id: String): Self = withPlugin(id, null)
  fun withPlugin(id: String, version: String?): Self

  fun withJavaPlugin(): Self
  fun withJavaLibraryPlugin(): Self
  fun withIdeaPlugin(): Self
  fun withKotlinJvmPlugin(): Self

  /**
   * Adds the Kotlin JVM plugin using the [version], or omitting the version call if [version] is null.
   */
  fun withKotlinJvmPlugin(version: String?): Self
  fun withKotlinJsPlugin(): Self
  fun withKotlinMultiplatformPlugin(): Self
  fun withKotlinJvmToolchain(jvmTarget: Int): Self
  fun withKotlinDsl(): Self
  fun withGroovyPlugin(): Self
  fun withGroovyPlugin(version: String): Self
  fun withApplicationPlugin(
    mainClass: String? = null,
    mainModule: String? = null,
    executableDir: String? = null,
    defaultJvmArgs: List<String>? = null,
  ): Self

  fun withKotlinTest(): Self
  fun withJUnit(): Self
  fun withJUnit4(): Self
  fun withJUnit5(): Self
  fun targetCompatibility(level: String): Self
  fun sourceCompatibility(level: String): Self

  // Note: These are Element building functions
  fun project(name: String): Expression
  fun project(name: String, configuration: String): Expression

  fun ScriptTreeBuilder.mavenRepository(url: String): ScriptTreeBuilder
  fun ScriptTreeBuilder.mavenCentral(): ScriptTreeBuilder
  fun ScriptTreeBuilder.mavenLocal(url: String): ScriptTreeBuilder

  companion object {

    @JvmStatic
    fun create(gradleVersion: GradleVersion, useKotlinDsl: Boolean): GradleBuildScriptBuilder<*> {
      return create(gradleVersion, GradleDsl.valueOf(useKotlinDsl))
    }

    @JvmStatic
    fun create(gradleVersion: GradleVersion, gradleDsl: GradleDsl): GradleBuildScriptBuilder<*> {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GroovyDslGradleBuildScriptBuilder.Impl(gradleVersion)
        GradleDsl.KOTLIN -> KotlinDslGradleBuildScriptBuilder.Impl(gradleVersion)
      }
    }

    @JvmStatic
    fun buildScript(gradleVersion: GradleVersion, gradleDsl: GradleDsl, configure: GradleBuildScriptBuilder<*>.() -> Unit): String {
      return create(gradleVersion, gradleDsl)
        .apply(configure)
        .generate()
    }

    @JvmStatic
    fun getBuildScriptName(gradleDsl: GradleDsl): String {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GradleConstants.DEFAULT_SCRIPT_NAME
        GradleDsl.KOTLIN -> GradleConstants.KOTLIN_DSL_SCRIPT_NAME
      }
    }
  }
}