// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractGradleScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder

internal abstract class AbstractGradleSettingScriptBuilderCore<Self : AbstractGradleSettingScriptBuilderCore<Self>>(
  override val gradleVersion: GradleVersion,
  override val gradleDsl: GradleDsl,
) : GradleSettingScriptBuilderCore<Self>,
    AbstractGradleScriptElementBuilder() {

  private var projectName: String? = null
  private val script = GradleScriptTreeBuilder.create()
  private var plugins = GradleScriptTreeBuilder.create()
  private var pluginManagement: GradleScriptTreeBuilder.() -> Unit = {}

  protected abstract fun apply(action: Self.() -> Unit): Self

  private fun applyAndMerge(builder: GradleScriptTreeBuilder, configure: GradleScriptTreeBuilder.() -> Unit): Self = apply {
    builder.addNonExistedElements(GradleScriptTreeBuilder.tree(configure))
  }

  override fun setProjectName(projectName: String): Self = apply {
    this.projectName = projectName
  }

  override fun setProjectDir(name: String, relativePath: String): Self = apply {
    when (gradleDsl) {
      GradleDsl.GROOVY -> addCode("""project('$name').projectDir = file('$relativePath')""")
      GradleDsl.KOTLIN -> addCode("""project("$name").projectDir = file("$relativePath")""")
    }
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

  override fun pluginManagement(configure: GradleScriptTreeBuilder.() -> Unit): Self = apply {
    pluginManagement = configure
  }

  override fun addCode(text: String): Self = apply {
    script.code(text)
  }

  override fun addCode(expression: Expression): Self = apply {
    script.addElement(expression)
  }

  override fun addCode(configure: GradleScriptTreeBuilder.() -> Unit): Self = apply {
    script.addElements(GradleScriptTreeBuilder.tree(configure))
  }

  override fun withPlugin(configure: GradleScriptTreeBuilder.() -> Unit): Self =
    applyAndMerge(plugins, configure)

  override fun withPlugin(id: String, version: String?): Self =
    withPlugin {
      when (version) {
        null -> call("id", id)
        else -> infixCall(call("id", id), "version", string(version))
      }
    }

  override fun generateTree(): BlockElement = GradleScriptTreeBuilder.tree {
    callIfNotEmpty("plugins", plugins)
    callIfNotEmpty("pluginManagement", pluginManagement)
    assignIfNotNull("rootProject.name", projectName)
    join(script)
  }

  override fun generate(): String {
    return GradleScriptBuilder.script(gradleDsl, generateTree())
  }
}