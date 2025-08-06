// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.AbstractGradleScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import java.util.function.Consumer

@ApiStatus.Internal
abstract class AbstractGradleBuildScriptBuilderCore<Self : GradleBuildScriptBuilderCore<Self>>(
  override val gradleVersion: GradleVersion,
  override val gradleDsl: GradleDsl
) : GradleBuildScriptBuilderCore<Self>,
    AbstractGradleScriptElementBuilder() {

  private val imports = GradleScriptTreeBuilder.create()
  private val buildScriptPrefixes = GradleScriptTreeBuilder.create()
  private val buildScriptDependencies = GradleScriptTreeBuilder.create()
  private val buildScriptRepositories = GradleScriptTreeBuilder.create()
  private val buildScriptPostfixes = GradleScriptTreeBuilder.create()
  private val plugins = GradleScriptTreeBuilder.create()
  private val java = GradleScriptTreeBuilder.create()
  private val prefixes = GradleScriptTreeBuilder.create()
  private val dependencies = GradleScriptTreeBuilder.create()
  private val repositories = GradleScriptTreeBuilder.create()
  private val postfixes = GradleScriptTreeBuilder.create()
  private val kotlin = GradleScriptTreeBuilder.create()

  protected abstract fun apply(action: Self.() -> Unit): Self

  private fun applyAndMerge(builder: GradleScriptTreeBuilder, configure: GradleScriptTreeBuilder.() -> Unit) =
    apply { builder.addNonExistedElements(GradleScriptTreeBuilder.tree(configure)) }

  override fun addImport(import: String): Self = applyAndMerge(imports) { code("import $import") }

  override fun addBuildScriptPrefix(vararg prefix: String): Self = withBuildScriptPrefix { code(*prefix) }
  override fun withBuildScriptPrefix(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptPrefixes, configure)
  override fun withBuildScriptPrefix(configure: Consumer<GradleScriptTreeBuilder>): Self = withBuildScriptPrefix(configure::accept)

  override fun addBuildScriptDependency(dependency: String): Self = withBuildScriptDependency { code(dependency) }
  override fun withBuildScriptDependency(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptDependencies, configure)
  override fun withBuildScriptDependency(configure: Consumer<GradleScriptTreeBuilder>): Self = withBuildScriptDependency(configure::accept)

  override fun addBuildScriptRepository(repository: String): Self = withBuildScriptRepository { code(repository) }
  override fun withBuildScriptRepository(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptRepositories, configure)
  override fun withBuildScriptRepository(configure: Consumer<GradleScriptTreeBuilder>): Self = withBuildScriptRepository(configure::accept)

  override fun addBuildScriptPostfix(vararg postfix: String): Self = withBuildScriptPostfix { code(*postfix) }
  override fun withBuildScriptPostfix(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(buildScriptPostfixes, configure)
  override fun withBuildScriptPostfix(configure: Consumer<GradleScriptTreeBuilder>): Self = withBuildScriptPostfix(configure::accept)

  override fun addPlugin(plugin: String): Self = withPlugin { code(plugin) }
  override fun withPlugin(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(plugins, configure)

  override fun addPrefix(vararg prefix: String): Self = withPrefix { code(*prefix) }
  override fun withPrefix(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(prefixes, configure)
  override fun withPrefix(configure: Consumer<GradleScriptTreeBuilder>): Self = withPrefix(configure::accept)

  override fun addDependency(dependency: String): Self = withDependency { code(dependency) }
  override fun withDependency(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(dependencies, configure)
  override fun withDependency(configure: Consumer<GradleScriptTreeBuilder>): Self = withDependency(configure::accept)

  override fun addRepository(repository: String): Self = withRepository { code(repository) }
  override fun withRepository(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(repositories, configure)
  override fun withRepository(configure: Consumer<GradleScriptTreeBuilder>): Self = withRepository(configure::accept)

  override fun addPostfix(vararg postfix: String): Self = withPostfix { code(*postfix) }
  override fun withPostfix(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(postfixes, configure)
  override fun withPostfix(configure: Consumer<GradleScriptTreeBuilder>): Self = withPostfix(configure::accept)

  override fun withJava(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(java, configure)
  override fun withJava(configure: Consumer<GradleScriptTreeBuilder>): Self = withPostfix(configure::accept)

  override fun withKotlin(configure: GradleScriptTreeBuilder.() -> Unit): Self = applyAndMerge(kotlin, configure)
  override fun withKotlin(configure: Consumer<GradleScriptTreeBuilder>): Self = withPostfix(configure::accept)

  override fun generateTree(): BlockElement = GradleScriptTreeBuilder.tree {
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
    callIfNotEmpty("kotlin", kotlin).ln()
    join(postfixes).ln()
  }

  override fun generate(): String {
    return GradleScriptBuilder.script(gradleDsl, generateTree())
  }
}