// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.util.function.Consumer

@ApiStatus.NonExtendable
interface GradleBuildScriptBuilderCore<out Self : GradleBuildScriptBuilderCore<Self>>
  : ScriptElementBuilder {

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
  fun addImport(import: String): Self

  /**
   * buildscript {
   *   ...
   *   [prefix]
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   * }
   */
  fun addBuildScriptPrefix(vararg prefix: String): Self
  fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * buildscript {
   *   dependencies {
   *     ...
   *     [dependency]
   *   }
   * }
   */
  fun addBuildScriptDependency(dependency: String): Self
  fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * buildscript {
   *   repositories {
   *     ...
   *     [repository]
   *   }
   * }
   */
  fun addBuildScriptRepository(repository: String): Self
  fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * buildscript {
   *   ...
   *   repositories { ... }
   *   dependencies { ... }
   *   ...
   *   [postfix]
   * }
   */
  fun addBuildScriptPostfix(vararg postfix: String): Self
  fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>): Self


  /**
   * plugins {
   *   ...
   *   [plugin]
   * }
   */
  fun addPlugin(plugin: String): Self
  fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): Self

  /**
   * buildscript { ... }
   * ...
   * [prefix]
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addPrefix(vararg prefix: String): Self
  fun withPrefix(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withPrefix(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * dependencies {
   *   ...
   *   [dependency]
   * }
   */
  fun addDependency(dependency: String): Self
  fun withDependency(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withDependency(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * repositories {
   *   ...
   *   [repository]
   * }
   */
  fun addRepository(repository: String): Self
  fun withRepository(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withRepository(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * java { ... }
   */
  fun withJava(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withJava(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   * [postfix]
   */
  fun addPostfix(vararg postfix: String): Self
  fun withPostfix(configure: ScriptTreeBuilder.() -> Unit): Self
  fun withPostfix(configure: Consumer<ScriptTreeBuilder>): Self

  /**
   * @return content for build.gradle
   */
  fun generate(): String

  /**
   * @return partial AST for build.gradle
   */
  fun generateTree(): BlockElement
}