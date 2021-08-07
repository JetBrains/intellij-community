// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.AbstractGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import kotlin.apply as applyKt


@Suppress("unused")
class BuildScriptDataBuilder(
  val buildScriptFile: VirtualFile,
  override val scriptBuilder: ScriptBuilder,
  gradleVersion: GradleVersion = GradleVersion.current()
) : AbstractGradleBuildScriptBuilder<BuildScriptDataBuilder>(gradleVersion) {

  override fun apply(action: BuildScriptDataBuilder.() -> Unit) = applyKt(action)

  override fun addImport(import: String) =
    super.addImport(import.trim().removePrefix("import "))

  fun addBuildscriptPropertyDefinition(definition: String) =
    addPrefix(definition.trim())

  fun addBuildscriptRepositoriesDefinition(definition: String) =
    addBuildScriptRepository(definition.trim())

  fun addBuildscriptDependencyNotation(notation: String) =
    addBuildScriptDependency(notation.trim())

  fun addPluginDefinitionInPluginsGroup(definition: String) =
    addPlugin(definition.trim())

  fun addPluginDefinition(definition: String) =
    addPrefix(definition.trim())

  fun addRepositoriesDefinition(definition: String) =
    addRepository(definition.trim())

  fun addDependencyNotation(notation: String) = apply {
    if (notation.matches("\\s*(compile|testCompile|runtime|testRuntime)[^\\w].*".toRegex())) {
      LOG.warn(notation)
      LOG.warn("compile, testCompile, runtime and testRuntime dependency notations were deprecated in Gradle 3.4, " +
               "use implementation, api, compileOnly and runtimeOnly instead", Throwable())
    }
    addDependency(notation.trim())
  }

  fun addPropertyDefinition(definition: String) =
    addPrefix(definition.trim())

  fun addOther(definition: String) =
    addPostfix(definition.trim())

  companion object {
    private val LOG = Logger.getInstance(BuildScriptDataBuilder::class.java)
  }
}