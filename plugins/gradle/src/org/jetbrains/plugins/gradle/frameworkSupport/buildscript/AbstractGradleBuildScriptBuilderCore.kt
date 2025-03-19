// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.tree
import java.util.function.Consumer

@ApiStatus.NonExtendable
abstract class AbstractGradleBuildScriptBuilderCore<Self : GradleBuildScriptBuilderCore<Self>>(
  override val gradleVersion: GradleVersion,
) : GradleBuildScriptBuilderCore<Self>,
    AbstractScriptElementBuilder() {

  private val imports = ScriptTreeBuilder()
  private val buildScriptPrefixes = ScriptTreeBuilder()
  private val buildScriptDependencies = ScriptTreeBuilder()
  private val buildScriptRepositories = ScriptTreeBuilder()
  private val buildScriptPostfixes = ScriptTreeBuilder()
  private val plugins = ScriptTreeBuilder()
  private val java = ScriptTreeBuilder()
  private val prefixes = ScriptTreeBuilder()
  private val dependencies = ScriptTreeBuilder()
  private val repositories = ScriptTreeBuilder()
  private val postfixes = ScriptTreeBuilder()

  protected abstract fun apply(action: Self.() -> Unit): Self

  private fun applyAndMerge(builder: ScriptTreeBuilder, configure: ScriptTreeBuilder.() -> Unit) =
    apply { builder.addNonExistedElements(configure) }

  override fun addImport(import: String): Self = applyAndMerge(imports) { code("import $import") }

  override fun addBuildScriptPrefix(vararg prefix: String): Self = withBuildScriptPrefix { code(*prefix) }
  override fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptPrefixes, configure)
  override fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>): Self = withBuildScriptPrefix(configure::accept)

  override fun addBuildScriptDependency(dependency: String): Self = withBuildScriptDependency { code(dependency) }
  override fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptDependencies, configure)
  override fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>): Self = withBuildScriptDependency(configure::accept)

  override fun addBuildScriptRepository(repository: String): Self = withBuildScriptRepository { code(repository) }
  override fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptRepositories, configure)
  override fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>): Self = withBuildScriptRepository(configure::accept)

  override fun addBuildScriptPostfix(vararg postfix: String): Self = withBuildScriptPostfix { code(*postfix) }
  override fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptPostfixes, configure)
  override fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>): Self = withBuildScriptPostfix(configure::accept)

  override fun addPlugin(plugin: String): Self = withPlugin { code(plugin) }
  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(plugins, configure)

  override fun addPrefix(vararg prefix: String): Self = withPrefix { code(*prefix) }
  override fun withPrefix(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(prefixes, configure)
  override fun withPrefix(configure: Consumer<ScriptTreeBuilder>): Self = withPrefix(configure::accept)

  override fun addDependency(dependency: String): Self = withDependency { code(dependency) }
  override fun withDependency(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(dependencies, configure)
  override fun withDependency(configure: Consumer<ScriptTreeBuilder>): Self = withDependency(configure::accept)

  override fun addRepository(repository: String): Self = withRepository { code(repository) }
  override fun withRepository(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(repositories, configure)
  override fun withRepository(configure: Consumer<ScriptTreeBuilder>): Self = withRepository(configure::accept)

  override fun addPostfix(vararg postfix: String): Self = withPostfix { code(*postfix) }
  override fun withPostfix(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(postfixes, configure)
  override fun withPostfix(configure: Consumer<ScriptTreeBuilder>): Self = withPostfix(configure::accept)

  override fun withJava(configure: ScriptTreeBuilder.() -> Unit): Self = applyAndMerge(java, configure)
  override fun withJava(configure: Consumer<ScriptTreeBuilder>): Self = withPostfix(configure::accept)

  override fun generateTree(): BlockElement = tree {
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
    callIfNotEmpty("java", java).ln()
    join(postfixes).ln()
  }
}