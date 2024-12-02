// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@ApiStatus.NonExtendable
abstract class AbstractGradleSettingScriptBuilder<Self : AbstractGradleSettingScriptBuilder<Self>>
  : AbstractScriptElementBuilder(),
    GradleSettingScriptBuilder<Self> {

  private var projectName: String? = null
  private val script = ScriptTreeBuilder()
  private var plugins = ScriptTreeBuilder()
  private var pluginManagement: ScriptTreeBuilder.() -> Unit = {}

  private val foojayResolverVersion = getFoojayResolverVersion()

  protected abstract fun apply(action: Self.() -> Unit): Self

  private fun applyAndMerge(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit) =
    apply { builder.addNonExistedElements(configure) }

  override fun setProjectName(projectName: String) = apply {
    this.projectName = projectName
  }

  override fun include(vararg name: String) = apply {
    script.call("include", *name)
  }

  override fun includeFlat(vararg name: String) = apply {
    script.call("includeFlat", *name)
  }

  override fun includeBuild(name: String) = apply {
    script.call("includeBuild", name)
  }

  override fun enableFeaturePreview(featureName: String) = apply {
    script.call("enableFeaturePreview", featureName)
  }

  override fun pluginManagement(configure: ScriptTreeBuilder.() -> Unit) {
    pluginManagement = configure
  }

  override fun addCode(text: String) = apply {
    script.code(text)
  }

  override fun addCode(expression: Expression) = apply {
    script.addElement(expression)
  }

  override fun addCode(configure: ScriptTreeBuilder.() -> Unit) = apply {
    script.addElements(configure)
  }

  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit) = apply {
    applyAndMerge(plugins, configure)
  }

  override fun withPlugin(id: String, version: String?) =
    withPlugin {
      when (version) {
        null -> call("id", id)
        else -> infixCall(call("id", id), "version", string(version))
      }
    }

  override fun withFoojayPlugin() {
    withPlugin("org.gradle.toolchains.foojay-resolver-convention", foojayResolverVersion)
  }

  override fun generateTree() = ScriptTreeBuilder.tree {
    callIfNotEmpty("plugins", plugins)
    callIfNotEmpty("pluginManagement", pluginManagement)
    assignIfNotNull("rootProject.name", projectName)
    join(script)
  }
}