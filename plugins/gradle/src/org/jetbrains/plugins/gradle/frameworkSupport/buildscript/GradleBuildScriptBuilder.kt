// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import java.io.File

interface GradleBuildScriptBuilder<SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilder<SB, BSB>> : GradleBuildScriptBuilderCore<SB, BSB> {

  fun group(group: String): BSB

  fun version(version: String): BSB

  fun addImplementationDependency(dependency: String, sourceSet: String? = null): BSB

  fun addRuntimeOnlyDependency(dependency: String, sourceSet: String? = null): BSB

  fun addTestImplementationDependency(dependency: String): BSB

  fun addTestRuntimeOnlyDependency(dependency: String): BSB

  fun addBuildScriptClasspath(dependency: String): BSB

  fun withTask(name: String, type: String? = null, configure: SB.() -> Unit = {}): BSB

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