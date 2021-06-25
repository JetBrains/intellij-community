// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import java.io.File

@Suppress("unused")
interface GradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>> : GradleBuildScriptBuilderCore<BSB> {

  fun addGroup(group: String): BSB
  fun addVersion(version: String): BSB

  fun addDependency(scope: String, dependency: String, sourceSet: String? = null): BSB
  fun addDependency(scope: String, dependency: Expression, sourceSet: String? = null): BSB

  fun addApiDependency(dependency: String, sourceSet: String? = null): BSB
  fun addApiDependency(dependency: Expression, sourceSet: String? = null): BSB

  fun addCompileOnlyDependency(dependency: String, sourceSet: String? = null): BSB
  fun addCompileOnlyDependency(dependency: Expression, sourceSet: String? = null): BSB

  fun addImplementationDependency(dependency: String, sourceSet: String? = null): BSB
  fun addImplementationDependency(dependency: Expression, sourceSet: String? = null): BSB

  fun addRuntimeOnlyDependency(dependency: String, sourceSet: String? = null): BSB
  fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String? = null): BSB

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
  fun withGroovyPlugin(): BSB
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