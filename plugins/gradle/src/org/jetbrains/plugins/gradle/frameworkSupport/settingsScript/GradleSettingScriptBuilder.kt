// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@ApiStatus.NonExtendable
interface GradleSettingScriptBuilder<Self: GradleSettingScriptBuilder<Self>> : ScriptElementBuilder {

  fun setProjectName(projectName: String): Self

  fun include(vararg name: String): Self

  fun includeFlat(vararg name: String): Self

  fun includeBuild(name: String): Self

  fun pluginManagement(configure: ScriptTreeBuilder.() -> Unit)

  fun enableFeaturePreview(featureName: String): Self

  fun addCode(text: String): Self

  fun addCode(expression: Expression): Self

  fun addCode(configure: ScriptTreeBuilder.() -> Unit): Self

  fun generateTree(): BlockElement

  fun generate(): String

  companion object {

    @JvmStatic
    fun create(useKotlinDsl: Boolean): GradleSettingScriptBuilder<*> {
      return when (useKotlinDsl) {
        true -> KotlinDslGradleSettingScriptBuilder()
        else -> GroovyDslGradleSettingScriptBuilder()
      }
    }
  }
}