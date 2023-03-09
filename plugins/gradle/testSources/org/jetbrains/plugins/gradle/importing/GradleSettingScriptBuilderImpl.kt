// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

class GradleSettingScriptBuilderImpl : AbstractScriptElementBuilder(), GradleSettingScriptBuilder {

  private var projectName: String? = null
  private val script = ScriptTreeBuilder()
  private var pluginManagement: ScriptTreeBuilder.() -> Unit = {}

  override fun setProjectName(projectName: String) = apply {
    this.projectName = projectName
  }

  override fun include(name: String) = apply {
    script.call("include", name)
  }

  override fun includeFlat(name: String) = apply {
    script.call("includeFlat", name)
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

  override fun generateTree() = ScriptTreeBuilder.tree {
    callIfNotEmpty("pluginManagement", pluginManagement)
    assignIfNotNull("rootProject.name", projectName)
    join(script)
  }

  override fun generate(useKotlinDsl: Boolean): String {
    val builder = when (useKotlinDsl) {
      true -> KotlinScriptBuilder()
      else -> GroovyScriptBuilder()
    }
    return builder.generate(generateTree())
  }
}