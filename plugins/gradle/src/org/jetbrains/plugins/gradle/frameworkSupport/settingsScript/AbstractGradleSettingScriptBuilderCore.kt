// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@ApiStatus.NonExtendable
abstract class AbstractGradleSettingScriptBuilderCore<Self : AbstractGradleSettingScriptBuilderCore<Self>>(
  override val gradleVersion: GradleVersion,
) : GradleSettingScriptBuilderCore<Self>,
    AbstractScriptElementBuilder() {

  private var projectName: String? = null
  private val script = ScriptTreeBuilder()
  private var plugins = ScriptTreeBuilder()
  private var pluginManagement: ScriptTreeBuilder.() -> Unit = {}

  protected abstract fun apply(action: Self.() -> Unit): Self

  private fun applyAndMerge(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit): Self = apply {
    builder.addNonExistedElements(configure)
  }

  override fun setProjectName(projectName: String): Self = apply {
    this.projectName = projectName
  }

  override fun include(vararg name: String): Self = apply {
    script.call("include", *name)
  }

  override fun includeFlat(vararg name: String): Self = apply {
    script.call("includeFlat", *name)
  }

  override fun includeBuild(name: String): Self = apply {
    script.call("includeBuild", name)
  }

  override fun enableFeaturePreview(featureName: String): Self = apply {
    script.call("enableFeaturePreview", featureName)
  }

  override fun pluginManagement(configure: ScriptTreeBuilder.() -> Unit): Self = apply {
    pluginManagement = configure
  }

  override fun addCode(text: String): Self = apply {
    script.code(text)
  }

  override fun addCode(expression: Expression): Self = apply {
    script.addElement(expression)
  }

  override fun addCode(configure: ScriptTreeBuilder.() -> Unit): Self = apply {
    script.addElements(configure)
  }

  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): Self =
    applyAndMerge(plugins, configure)

  override fun withPlugin(id: String, version: String?): Self =
    withPlugin {
      when (version) {
        null -> call("id", id)
        else -> infixCall(call("id", id), "version", string(version))
      }
    }

  override fun generateTree(): BlockElement = ScriptTreeBuilder.tree {
    callIfNotEmpty("plugins", plugins)
    callIfNotEmpty("pluginManagement", pluginManagement)
    assignIfNotNull("rootProject.name", projectName)
    join(script)
  }
}