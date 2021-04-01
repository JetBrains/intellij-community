// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import java.util.function.Consumer

interface GradleBuildScriptBuilderCore<SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilderCore<SB, BSB>> {
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
  fun withBuildScriptPrefix(configure: SB.() -> Unit): BSB
  fun withBuildScriptPrefix(configure: Consumer<SB>): BSB

  /**
   * buildscript {
   *   dependencies {
   *     ...
   *     [dependency]
   *   }
   * }
   */
  fun addBuildScriptDependency(dependency: String): BSB
  fun withBuildScriptDependency(configure: SB.() -> Unit): BSB
  fun withBuildScriptDependency(configure: Consumer<SB>): BSB

  /**
   * buildscript {
   *   repositories {
   *     ...
   *     [repository]
   *   }
   * }
   */
  fun addBuildScriptRepository(repository: String): BSB
  fun withBuildScriptRepository(configure: SB.() -> Unit): BSB
  fun withBuildScriptRepository(configure: Consumer<SB>): BSB

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
  fun withBuildScriptPostfix(configure: SB.() -> Unit): BSB
  fun withBuildScriptPostfix(configure: Consumer<SB>): BSB

  /**
   * plugins {
   *   ...
   *   groovy: id [id] version [version]
   *   kotlin: id([id]) version [version]
   * }
   */
  fun addPlugin(id: String, version: String? = null): BSB

  /**
   * groovy: apply plugin: [plugin]
   * kotlin: apply(plugin = [plugin])
   */
  fun applyPlugin(plugin: String): BSB

  /**
   * buildscript { ... }
   * ...
   * [prefix]
   * repositories { ... }
   * dependencies { ... }
   * ...
   */
  fun addPrefix(vararg prefix: String): BSB
  fun withPrefix(configure: SB.() -> Unit): BSB
  fun withPrefix(configure: Consumer<SB>): BSB

  /**
   * dependencies {
   *   ...
   *   [dependency]
   * }
   */
  fun addDependency(dependency: String): BSB
  fun withDependency(configure: SB.() -> Unit): BSB
  fun withDependency(configure: Consumer<SB>): BSB

  /**
   * repositories {
   *   ...
   *   [repository]
   * }
   */
  fun addRepository(repository: String): BSB
  fun withRepository(configure: SB.() -> Unit): BSB
  fun withRepository(configure: Consumer<SB>): BSB

  /**
   * buildscript { ... }
   * ...
   * repositories { ... }
   * dependencies { ... }
   * ...
   * [postfix]
   */
  fun addPostfix(vararg postfix: String): BSB
  fun withPostfix(configure: SB.() -> Unit): BSB
  fun withPostfix(configure: Consumer<SB>): BSB

  /**
   * @return content for build.gradle
   */
  fun generate(): String

  /**
   * Surrounds string by [ScriptBuilder.str]
   */
  fun str(string: String): String
}