// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import java.util.function.Consumer

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class AbstractGradleBuildScriptBuilderCore<SB : ScriptBuilder<SB>, BSB : AbstractGradleBuildScriptBuilderCore<SB, BSB>>
  : GradleBuildScriptBuilderCore<SB, BSB> {

  private val imports by lazy(::createScriptBuilder)
  private val buildScriptPrefixes by lazy(::createScriptBuilder)
  private val buildScriptDependencies by lazy(::createScriptBuilder)
  private val buildScriptRepositories by lazy(::createScriptBuilder)
  private val buildScriptPostfixes by lazy(::createScriptBuilder)
  private val plugins by lazy(::createScriptBuilder)
  private val applicablePlugins by lazy(::createScriptBuilder)
  private val prefixes by lazy(::createScriptBuilder)
  private val dependencies by lazy(::createScriptBuilder)
  private val repositories by lazy(::createScriptBuilder)
  private val postfixes by lazy(::createScriptBuilder)

  protected abstract fun apply(action: BSB.() -> Unit): BSB

  protected abstract fun createScriptBuilder(): SB

  private fun apply(builder: SB, configure: SB.() -> Unit) = apply { builder.configure() }
  private fun applyIfNotContains(builder: SB, configure: SB.() -> Unit) = apply {
    val childBuilder = createScriptBuilder()
    childBuilder.configure()
    if (childBuilder !in builder) {
      builder.join(childBuilder)
    }
  }

  override fun str(string: String) = createScriptBuilder().str(string)

  override fun addImport(import: String) = applyIfNotContains(imports) { code("import $import") }

  override fun addBuildScriptPrefix(vararg prefix: String) = withBuildScriptPrefix { code(*prefix) }
  override fun withBuildScriptPrefix(configure: SB.() -> Unit) = apply(buildScriptPrefixes, configure)
  override fun withBuildScriptPrefix(configure: Consumer<SB>) = withBuildScriptPrefix(configure::accept)

  override fun addBuildScriptDependency(dependency: String) = withBuildScriptDependency { code(dependency) }
  override fun withBuildScriptDependency(configure: SB.() -> Unit) = applyIfNotContains(buildScriptDependencies, configure)
  override fun withBuildScriptDependency(configure: Consumer<SB>) = withBuildScriptDependency(configure::accept)

  override fun addBuildScriptRepository(repository: String) = withBuildScriptRepository { code(repository) }
  override fun withBuildScriptRepository(configure: SB.() -> Unit) = applyIfNotContains(buildScriptRepositories, configure)
  override fun withBuildScriptRepository(configure: Consumer<SB>) = withBuildScriptRepository(configure::accept)

  override fun addBuildScriptPostfix(vararg postfix: String) = withBuildScriptPostfix { code(*postfix) }
  override fun withBuildScriptPostfix(configure: SB.() -> Unit) = apply(buildScriptPostfixes, configure)
  override fun withBuildScriptPostfix(configure: Consumer<SB>) = withBuildScriptPostfix(configure::accept)

  protected fun withPlugin(configure: SB.() -> Unit) = applyIfNotContains(plugins, configure)
  override fun applyPlugin(plugin: String) = applyIfNotContains(applicablePlugins) { call("apply", "plugin" to plugin) }

  override fun addPrefix(vararg prefix: String) = withPrefix { code(*prefix) }
  override fun withPrefix(configure: SB.() -> Unit) = apply(prefixes, configure)
  override fun withPrefix(configure: Consumer<SB>) = withPrefix(configure::accept)

  override fun addDependency(dependency: String) = withDependency { code(dependency) }
  override fun withDependency(configure: SB.() -> Unit) = applyIfNotContains(dependencies, configure)
  override fun withDependency(configure: Consumer<SB>) = withDependency(configure::accept)

  override fun addRepository(repository: String) = withRepository { code(repository) }
  override fun withRepository(configure: SB.() -> Unit) = applyIfNotContains(repositories, configure)
  override fun withRepository(configure: Consumer<SB>) = withRepository(configure::accept)

  override fun addPostfix(vararg postfix: String) = withPostfix { code(*postfix) }
  override fun withPostfix(configure: SB.() -> Unit) = apply(postfixes, configure)
  override fun withPostfix(configure: Consumer<SB>) = withPostfix(configure::accept)

  override fun generate() = createScriptBuilder().apply {
    join(imports)
    blockIfNotEmpty("buildscript") {
      join(buildScriptPrefixes)
      blockIfNotEmpty("repositories", buildScriptRepositories)
      blockIfNotEmpty("dependencies", buildScriptDependencies)
      join(buildScriptPostfixes)
    }
    blockIfNotEmpty("plugins", plugins)
    join(applicablePlugins)
    join(prefixes)
    blockIfNotEmpty("repositories", repositories)
    blockIfNotEmpty("dependencies", dependencies)
    join(postfixes)
  }.generate()
}