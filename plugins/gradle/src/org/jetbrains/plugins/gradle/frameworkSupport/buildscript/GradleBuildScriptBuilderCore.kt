// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import java.util.function.Consumer

@ApiStatus.NonExtendable
interface GradleBuildScriptBuilderCore<out Self : GradleBuildScriptBuilderCore<Self>>
  : GradleScriptElementBuilder {

  val gradleVersion: GradleVersion

  val gradleDsl: GradleDsl

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
  fun withBuildScriptPrefix(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptPrefix(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * buildscript {
   *   dependencies {
   *     ...
   *     [dependency]
   *   }
   * }
   */
  fun addBuildScriptDependency(dependency: String): Self
  fun withBuildScriptDependency(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptDependency(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * buildscript {
   *   repositories {
   *     ...
   *     [repository]
   *   }
   * }
   */
  fun addBuildScriptRepository(repository: String): Self
  fun withBuildScriptRepository(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptRepository(configure: Consumer<GradleScriptTreeBuilder>): Self

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
  fun withBuildScriptPostfix(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withBuildScriptPostfix(configure: Consumer<GradleScriptTreeBuilder>): Self


  /**
   * plugins {
   *   ...
   *   [plugin]
   * }
   */
  fun addPlugin(plugin: String): Self
  fun withPlugin(configure: GradleScriptTreeBuilder.() -> Unit): Self

  /**
   * buildscript { ... }
   * ...
   * [prefix]
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addPrefix(vararg prefix: String): Self
  fun withPrefix(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withPrefix(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * dependencies {
   *   ...
   *   [dependency]
   * }
   */
  fun addDependency(dependency: String): Self
  fun withDependency(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withDependency(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * repositories {
   *   ...
   *   [repository]
   * }
   */
  fun addRepository(repository: String): Self
  fun withRepository(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withRepository(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * java { ... }
   */
  fun withJava(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withJava(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * kotlin { ... }
   */
  fun withKotlin(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withKotlin(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   * [postfix]
   */
  fun addPostfix(vararg postfix: String): Self
  fun withPostfix(configure: GradleScriptTreeBuilder.() -> Unit): Self
  fun withPostfix(configure: Consumer<GradleScriptTreeBuilder>): Self

  /**
   * @return content for build.gradle
   */
  fun generate(): String

  /**
   * @return partial AST for build.gradle
   */
  fun generateTree(): BlockElement
}