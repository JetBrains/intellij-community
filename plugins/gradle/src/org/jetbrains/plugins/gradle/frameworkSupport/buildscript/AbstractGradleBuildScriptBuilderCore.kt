// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.script
import java.util.function.Consumer

@Suppress("unused")
abstract class AbstractGradleBuildScriptBuilderCore<BSB : AbstractGradleBuildScriptBuilderCore<BSB>>
  : GradleBuildScriptBuilderCore<BSB>, AbstractScriptElementBuilder() {

  private val imports = ScriptTreeBuilder()
  private val buildScriptPrefixes = ScriptTreeBuilder()
  private val buildScriptDependencies = ScriptTreeBuilder()
  private val buildScriptRepositories = ScriptTreeBuilder()
  private val buildScriptPostfixes = ScriptTreeBuilder()
  private val plugins = ScriptTreeBuilder()
  private val applicablePlugins = ScriptTreeBuilder()
  private val prefixes = ScriptTreeBuilder()
  private val dependencies = ScriptTreeBuilder()
  private val repositories = ScriptTreeBuilder()
  private val postfixes = ScriptTreeBuilder()

  protected abstract val scriptBuilder: ScriptBuilder

  protected abstract fun apply(action: BSB.() -> Unit): BSB

  private fun apply(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit) = apply { builder.configure() }
  private fun applyIfNotContains(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit) = apply {
    val childBuilder = ScriptTreeBuilder(configure)
    if (childBuilder !in builder) {
      builder.join(childBuilder)
    }
  }

  override fun addImport(import: String) = applyIfNotContains(imports) { code("import $import") }

  override fun addBuildScriptPrefix(vararg prefix: String) = withBuildScriptPrefix { code(*prefix) }
  override fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit) = apply(buildScriptPrefixes, configure)
  override fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptPrefix(configure::accept)

  override fun addBuildScriptDependency(dependency: String) = withBuildScriptDependency { code(dependency) }
  override fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit) = applyIfNotContains(buildScriptDependencies, configure)
  override fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptDependency(configure::accept)

  override fun addBuildScriptRepository(repository: String) = withBuildScriptRepository { code(repository) }
  override fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit) = applyIfNotContains(buildScriptRepositories, configure)
  override fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptRepository(configure::accept)

  override fun addBuildScriptPostfix(vararg postfix: String) = withBuildScriptPostfix { code(*postfix) }
  override fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit) = apply(buildScriptPostfixes, configure)
  override fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptPostfix(configure::accept)

  override fun addPlugin(id: String, version: String?) = applyIfNotContains(plugins) {
    when (version) {
      null -> call("id", id)
      else -> infixCall(call("id", id), "version", string(version))
    }
  }

  override fun applyPlugin(plugin: String) = applyIfNotContains(applicablePlugins) { call("apply", "plugin" to plugin) }

  override fun addPrefix(vararg prefix: String) = withPrefix { code(*prefix) }
  override fun withPrefix(configure: ScriptTreeBuilder.() -> Unit) = apply(prefixes, configure)
  override fun withPrefix(configure: Consumer<ScriptTreeBuilder>) = withPrefix(configure::accept)

  override fun addDependency(dependency: String) = withDependency { code(dependency) }
  override fun withDependency(configure: ScriptTreeBuilder.() -> Unit) = applyIfNotContains(dependencies, configure)
  override fun withDependency(configure: Consumer<ScriptTreeBuilder>) = withDependency(configure::accept)

  override fun addRepository(repository: String) = withRepository { code(repository) }
  override fun withRepository(configure: ScriptTreeBuilder.() -> Unit) = applyIfNotContains(repositories, configure)
  override fun withRepository(configure: Consumer<ScriptTreeBuilder>) = withRepository(configure::accept)

  override fun addPostfix(vararg postfix: String) = withPostfix { code(*postfix) }
  override fun withPostfix(configure: ScriptTreeBuilder.() -> Unit) = apply(postfixes, configure)
  override fun withPostfix(configure: Consumer<ScriptTreeBuilder>) = withPostfix(configure::accept)

  override fun generate() = script(scriptBuilder) {
    join(imports)
    callIfNotEmpty("buildscript") {
      join(buildScriptPrefixes)
      callIfNotEmpty("repositories", buildScriptRepositories)
      callIfNotEmpty("dependencies", buildScriptDependencies)
      join(buildScriptPostfixes)
    }
    callIfNotEmpty("plugins", plugins)
    join(applicablePlugins)
    join(prefixes)
    callIfNotEmpty("repositories", repositories)
    callIfNotEmpty("dependencies", dependencies)
    join(postfixes)
  }
}