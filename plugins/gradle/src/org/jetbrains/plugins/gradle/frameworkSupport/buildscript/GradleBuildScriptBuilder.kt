// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Consumer

@ApiStatus.NonExtendable
interface GradleBuildScriptBuilder<Self : GradleBuildScriptBuilder<Self>>
  : GradleBuildScriptBuilderCore<Self> {

  fun addGroup(group: String): Self
  fun addVersion(version: String): Self

  fun registerTask(name: String, type: String?, configure: Consumer<GradleScriptTreeBuilder>): Self = registerTask(name, type, configure::accept)
  fun registerTask(name: String, type: String? = null, configure: GradleScriptTreeBuilder.() -> Unit = {}): Self

  fun configureTask(name: String, type: String, configure: Consumer<GradleScriptTreeBuilder>): Self = configureTask(name, type, configure::accept)
  fun configureTask(name: String, type: String, configure: GradleScriptTreeBuilder.() -> Unit): Self

  @Deprecated("Renamed, use the [test] function instead.")
  fun configureTestTask(configure: GradleScriptTreeBuilder.() -> Unit): Self = test(configure)

  fun test(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun test(configure: Consumer<GradleScriptTreeBuilder>): Self =
    test(configure::accept)

  fun compileJava(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun compileJava(configure: Consumer<GradleScriptTreeBuilder>): Self =
    compileJava(configure::accept)

  fun compileTestJava(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun compileTestJava(configure: Consumer<GradleScriptTreeBuilder>): Self =
    compileTestJava(configure::accept)

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

  fun withJava(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withJava(configure: Consumer<GradleScriptTreeBuilder>): Self = withJava(configure::accept)
  fun withJavaPlugin(): Self
  fun withJavaLibraryPlugin(): Self
  fun withJavaToolchain(languageVersion: Int): Self

  fun withIdeaPlugin(): Self

  fun withKotlin(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withKotlin(configure: Consumer<GradleScriptTreeBuilder>): Self = withKotlin(configure::accept)
  fun withKotlinJvmPlugin(): Self
  fun withKotlinJvmPlugin(version: String?): Self
  fun withKotlinJsPlugin(): Self
  fun withKotlinMultiplatformPlugin(): Self
  fun withKotlinJvmToolchain(jvmTarget: Int): Self

  fun withKotlinDsl(): Self

  fun withGroovyPlugin(): Self
  fun withGroovyPlugin(version: String): Self

  fun withApplication(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withApplication(configure: Consumer<GradleScriptTreeBuilder>): Self = withApplication(configure::accept)
  fun withApplicationPlugin(): Self
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
  fun withJUnit6(): Self
  fun targetCompatibility(level: String): Self
  fun sourceCompatibility(level: String): Self

  // Note: These are Element building functions
  fun project(name: String): Expression
  fun project(name: String, configuration: String): Expression

  fun GradleScriptTreeBuilder.mavenRepository(url: String): GradleScriptTreeBuilder
  fun GradleScriptTreeBuilder.mavenCentral(): GradleScriptTreeBuilder
  fun GradleScriptTreeBuilder.mavenLocal(url: String): GradleScriptTreeBuilder

  companion object {

    @JvmStatic
    fun create(gradleVersion: GradleVersion, useKotlinDsl: Boolean): GradleBuildScriptBuilder<*> {
      return create(gradleVersion, GradleDsl.valueOf(useKotlinDsl))
    }

    @JvmStatic
    fun create(gradleVersion: GradleVersion, gradleDsl: GradleDsl): GradleBuildScriptBuilder<*> {
      return AbstractGradleBuildScriptBuilder.Impl(gradleVersion, gradleDsl)
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