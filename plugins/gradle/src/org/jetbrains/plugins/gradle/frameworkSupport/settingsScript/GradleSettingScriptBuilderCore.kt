// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder

@ApiStatus.NonExtendable
interface GradleSettingScriptBuilderCore<Self : GradleSettingScriptBuilderCore<Self>>
  : ScriptElementBuilder {

  val gradleVersion: GradleVersion

  fun setProjectName(projectName: String): Self

  fun setProjectDir(name: String, relativePath: String): Self

  fun include(vararg name: String): Self

  fun includeFlat(vararg name: String): Self

  fun includeBuild(name: String): Self

  fun pluginManagement(configure: ScriptTreeBuilder.() -> Unit): Self

  fun enableFeaturePreview(featureName: String): Self

  fun addCode(text: String): Self

  fun addCode(expression: Expression): Self

  fun addCode(configure: ScriptTreeBuilder.() -> Unit): Self

  fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): Self

  fun withPlugin(id: String, version: String?): Self

  fun generateTree(): BlockElement

  fun generate(): String
}