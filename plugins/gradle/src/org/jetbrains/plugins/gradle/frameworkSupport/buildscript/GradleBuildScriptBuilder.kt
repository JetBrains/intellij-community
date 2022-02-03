// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.io.File
import java.util.function.Consumer

@Suppress("unused")
interface GradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>> : GradleBuildScriptBuilderCore<BSB> {

  fun addGroup(group: String): BSB
  fun addVersion(version: String): BSB

  fun configureTask(name: String, configure: ScriptTreeBuilder.() -> Unit): BSB
  fun configureTask(name: String, configure: Consumer<ScriptTreeBuilder>): BSB

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
  fun addBuildScriptClasspath(vararg dependencies: File): BSB

  fun withMavenCentral(): BSB
  fun withBuildScriptMavenCentral(): BSB

  fun withPlugin(id: String, version: String? = null): BSB

  fun withJavaPlugin(): BSB
  fun withJavaLibraryPlugin(): BSB
  fun withIdeaPlugin(): BSB
  fun withKotlinJvmPlugin(): BSB
  fun withKotlinJsPlugin(): BSB
  fun withKotlinMultiplatformPlugin(): BSB
  fun withGroovyPlugin(version: String = getGroovyVersion()): BSB
  fun withApplicationPlugin(
    mainClass: String? = null,
    mainModule: String? = null,
    executableDir: String? = null,
    defaultJvmArgs: List<String>? = null
  ): BSB

  fun withJUnit(): BSB
  fun withJUnit4(): BSB
  fun withJUnit5(): BSB
}