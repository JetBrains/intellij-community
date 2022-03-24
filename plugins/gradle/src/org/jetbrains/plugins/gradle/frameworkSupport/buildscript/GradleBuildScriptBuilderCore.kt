// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.util.function.Consumer

@Suppress("unused")
interface GradleBuildScriptBuilderCore<out BSB : GradleBuildScriptBuilderCore<BSB>> : ScriptElementBuilder {

  val gradleVersion: GradleVersion

  /**
   * ...
   * import [import]
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addImport(import: String): BSB

  /**
   * buildscript {
   *   ...
   *   [prefix]
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   * }
   */
  fun addBuildScriptPrefix(vararg prefix: String): BSB
  fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * buildscript {
   *   dependencies {
   *     ...
   *     [dependency]
   *   }
   * }
   */
  fun addBuildScriptDependency(dependency: String): BSB
  fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * buildscript {
   *   repositories {
   *     ...
   *     [repository]
   *   }
   * }
   */
  fun addBuildScriptRepository(repository: String): BSB
  fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * buildscript {
   *   ...
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   *   [postfix]
   * }
   */
  fun addBuildScriptPostfix(vararg postfix: String): BSB
  fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>): BSB


  /**
   * plugins {
   *   ...
   *   [plugin]
   * }
   */
  fun addPlugin(plugin: String): BSB
  fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): BSB

  /**
   * buildscript { ... }
   * ...
   * [prefix]
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addPrefix(vararg prefix: String): BSB
  fun withPrefix(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withPrefix(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * dependencies {
   *   ...
   *   [dependency]
   * }
   */
  fun addDependency(dependency: String): BSB
  fun withDependency(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withDependency(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * repositories {
   *   ...
   *   [repository]
   * }
   */
  fun addRepository(repository: String): BSB
  fun withRepository(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withRepository(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   * [postfix]
   */
  fun addPostfix(vararg postfix: String): BSB
  fun withPostfix(configure: ScriptTreeBuilder.() -> Unit): BSB
  fun withPostfix(configure: Consumer<ScriptTreeBuilder>): BSB

  /**
   * @return content for build.gradle
   */
  fun generate(): String

  /**
   * @return partial AST for build.gradle
   */
  fun generateTree(): BlockElement
}