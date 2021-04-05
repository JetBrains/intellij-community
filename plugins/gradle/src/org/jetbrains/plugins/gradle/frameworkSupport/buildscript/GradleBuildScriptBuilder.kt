// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.io.File

@Suppress("unused")
interface GradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>> : GradleBuildScriptBuilderCore<BSB> {

  fun addGroup(group: String): BSB
  fun addVersion(version: String): BSB

  fun addDependency(scope: String, dependency: String): BSB
  fun addDependency(scope: String, dependency: Expression): BSB

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

  fun withTask(name: String, type: String? = null, configure: ScriptTreeBuilder.() -> Unit = {}): BSB

  fun withBuildScriptMavenCentral(useOldStyleMetadata: Boolean = false): BSB

  fun withMavenCentral(useOldStyleMetadata: Boolean = false): BSB

  fun withJavaPlugin(): BSB

  fun withIdeaPlugin(): BSB

  fun withKotlinPlugin(version: String): BSB

  fun withGroovyPlugin(): BSB

  fun withApplicationPlugin(mainClassName: String): BSB

  fun withJUnit(): BSB

  fun withJUnit4(): BSB

  fun withJUnit5(): BSB

  fun withGradleIdeaExtPluginIfCan(): BSB

  fun withGradleIdeaExtPlugin(): BSB

  fun withLocalGradleIdeaExtPlugin(jarFile: File): BSB
}