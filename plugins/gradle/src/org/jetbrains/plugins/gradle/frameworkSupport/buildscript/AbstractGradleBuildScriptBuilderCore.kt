// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.tree
import java.util.function.Consumer

@ApiStatus.NonExtendable
abstract class AbstractGradleBuildScriptBuilderCore<BSB : GradleBuildScriptBuilderCore<BSB>>(
  override val gradleVersion: GradleVersion
) : GradleBuildScriptBuilderCore<BSB>, AbstractScriptElementBuilder() {

  private val imports = ScriptTreeBuilder()
  private val buildScriptPrefixes = ScriptTreeBuilder()
  private val buildScriptDependencies = ScriptTreeBuilder()
  private val buildScriptRepositories = ScriptTreeBuilder()
  private val buildScriptPostfixes = ScriptTreeBuilder()
  private val plugins = ScriptTreeBuilder()
  private val prefixes = ScriptTreeBuilder()
  private val dependencies = ScriptTreeBuilder()
  private val repositories = ScriptTreeBuilder()
  private val postfixes = ScriptTreeBuilder()

  protected abstract fun apply(action: BSB.() -> Unit): BSB

  private fun applyAndMerge(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit) =
    apply { builder.addNonExistedElements(configure) }

  override fun addImport(import: String) = applyAndMerge(imports) { code("import $import") }

  override fun addBuildScriptPrefix(vararg prefix: String) = withBuildScriptPrefix { code(*prefix) }
  override fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(buildScriptPrefixes, configure)
  override fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptPrefix(configure::accept)

  override fun addBuildScriptDependency(dependency: String) = withBuildScriptDependency { code(dependency) }
  override fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(buildScriptDependencies, configure)
  override fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptDependency(configure::accept)

  override fun addBuildScriptRepository(repository: String) = withBuildScriptRepository { code(repository) }
  override fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(buildScriptRepositories, configure)
  override fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptRepository(configure::accept)

  override fun addBuildScriptPostfix(vararg postfix: String) = withBuildScriptPostfix { code(*postfix) }
  override fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(buildScriptPostfixes, configure)
  override fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>) = withBuildScriptPostfix(configure::accept)

  override fun addPlugin(plugin: String) = withPlugin { code(plugin) }
  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(plugins, configure)

  override fun addPrefix(vararg prefix: String) = withPrefix { code(*prefix) }
  override fun withPrefix(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(prefixes, configure)
  override fun withPrefix(configure: Consumer<ScriptTreeBuilder>) = withPrefix(configure::accept)

  override fun addDependency(dependency: String) = withDependency { code(dependency) }
  override fun withDependency(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(dependencies, configure)
  override fun withDependency(configure: Consumer<ScriptTreeBuilder>) = withDependency(configure::accept)

  override fun addRepository(repository: String) = withRepository { code(repository) }
  override fun withRepository(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(repositories, configure)
  override fun withRepository(configure: Consumer<ScriptTreeBuilder>) = withRepository(configure::accept)

  override fun addPostfix(vararg postfix: String) = withPostfix { code(*postfix) }
  override fun withPostfix(configure: ScriptTreeBuilder.() -> Unit) = applyAndMerge(postfixes, configure)
  override fun withPostfix(configure: Consumer<ScriptTreeBuilder>) = withPostfix(configure::accept)

  override fun generateTree() = tree {
    join(imports).ln()
    callIfNotEmpty("buildscript") {
      join(buildScriptPrefixes).ln()
      callIfNotEmpty("repositories", buildScriptRepositories).ln()
      callIfNotEmpty("dependencies", buildScriptDependencies).ln()
      join(buildScriptPostfixes).ln()
    }.ln()
    callIfNotEmpty("plugins", plugins).ln()
    join(prefixes).ln()
    callIfNotEmpty("repositories", repositories).ln()
    callIfNotEmpty("dependencies", dependencies).ln()
    join(postfixes).ln()
  }
}