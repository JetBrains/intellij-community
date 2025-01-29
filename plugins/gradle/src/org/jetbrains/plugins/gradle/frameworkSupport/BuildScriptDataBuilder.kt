// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.util.function.Consumer


class BuildScriptDataBuilder(
  val buildScriptFile: VirtualFile,
  private val backend: GradleBuildScriptBuilder<*>,
) : GradleBuildScriptBuilder<BuildScriptDataBuilder>,
    ScriptElementBuilder by backend {

  fun addBuildscriptPropertyDefinition(definition: String): BuildScriptDataBuilder =
    apply { addPrefix(definition.trim()) }

  fun addBuildscriptRepositoriesDefinition(definition: String): BuildScriptDataBuilder =
    apply { addBuildScriptRepository(definition.trim()) }

  fun addBuildscriptDependencyNotation(notation: String): BuildScriptDataBuilder =
    apply { addBuildScriptDependency(notation.trim()) }

  fun addPluginDefinitionInPluginsGroup(definition: String): BuildScriptDataBuilder =
    apply { addPlugin(definition.trim()) }

  fun addPluginDefinition(definition: String): BuildScriptDataBuilder =
    apply { addPrefix(definition.trim()) }

  fun addRepositoriesDefinition(definition: String): BuildScriptDataBuilder =
    apply { addRepository(definition.trim()) }

  fun addDependencyNotation(notation: String): BuildScriptDataBuilder = apply {
    if (notation.matches("\\s*(compile|testCompile|runtime|testRuntime)[^\\w].*".toRegex())) {
      LOG.warn(notation)
      LOG.warn("compile, testCompile, runtime and testRuntime dependency notations were deprecated in Gradle 3.4, " +
               "use implementation, api, compileOnly and runtimeOnly instead", Throwable())
    }
    addDependency(notation.trim())
  }

  fun addPropertyDefinition(definition: String): BuildScriptDataBuilder =
    apply { addPrefix(definition.trim()) }

  fun addOther(definition: String): BuildScriptDataBuilder =
    apply { addPostfix(definition.trim()) }

  // @formatter:off
  override val gradleVersion: GradleVersion by backend::gradleVersion
  override fun addImport(import: String): BuildScriptDataBuilder = apply { backend.addImport(import) }
  override fun addBuildScriptPrefix(vararg prefix: String): BuildScriptDataBuilder = apply { backend.addBuildScriptPrefix(*prefix) }
  override fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withBuildScriptPrefix(configure) }
  override fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withBuildScriptPrefix(configure) }
  override fun addBuildScriptDependency(dependency: String): BuildScriptDataBuilder = apply { backend.addBuildScriptDependency(dependency) }
  override fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withBuildScriptDependency(configure) }
  override fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withBuildScriptDependency(configure) }
  override fun addBuildScriptRepository(repository: String): BuildScriptDataBuilder = apply { backend.addBuildScriptRepository(repository) }
  override fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withBuildScriptRepository(configure) }
  override fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withBuildScriptRepository(configure) }
  override fun addBuildScriptPostfix(vararg postfix: String): BuildScriptDataBuilder = apply { backend.addBuildScriptPostfix(*postfix) }
  override fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withBuildScriptPostfix(configure) }
  override fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withBuildScriptPostfix(configure) }
  override fun addPlugin(plugin: String): BuildScriptDataBuilder = apply { backend.addPlugin(plugin) }
  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withPlugin(configure) }
  override fun addPrefix(vararg prefix: String): BuildScriptDataBuilder = apply { backend.addPrefix(*prefix) }
  override fun withPrefix(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withPrefix(configure) }
  override fun withPrefix(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withPrefix(configure) }
  override fun addDependency(dependency: String): BuildScriptDataBuilder = apply { backend.addDependency(dependency) }
  override fun withDependency(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withDependency(configure) }
  override fun withDependency(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withDependency(configure) }
  override fun addRepository(repository: String): BuildScriptDataBuilder = apply { backend.addRepository(repository) }
  override fun withRepository(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withRepository(configure) }
  override fun withRepository(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withRepository(configure) }
  override fun addPostfix(vararg postfix: String): BuildScriptDataBuilder = apply { backend.addPostfix(*postfix) }
  override fun withPostfix(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withPostfix(configure) }
  override fun withPostfix(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withPostfix(configure) }
  override fun generate(): String = backend.generate()
  override fun generateTree(): BlockElement = backend.generateTree()

  override fun addGroup(group: String): BuildScriptDataBuilder = apply { backend.addGroup(group) }
  override fun addVersion(version: String): BuildScriptDataBuilder = apply { backend.addVersion(version) }
  override fun configureTask(name: String, type: String, configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.configureTask(name, type, configure) }
  override fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.configureTestTask(configure) }
  override fun addDependency(scope: String, dependency: String, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addDependency(scope, dependency, sourceSet) }
  override fun addDependency(scope: String, dependency: Expression, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addDependency(scope, dependency, sourceSet) }
  override fun addApiDependency(dependency: String, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addApiDependency(dependency, sourceSet) }
  override fun addApiDependency(dependency: Expression, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addApiDependency(dependency, sourceSet) }
  override fun addCompileOnlyDependency(dependency: String, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addCompileOnlyDependency(dependency, sourceSet) }
  override fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addCompileOnlyDependency(dependency, sourceSet) }
  override fun addImplementationDependency(dependency: String, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addImplementationDependency(dependency, sourceSet) }
  override fun addImplementationDependency(dependency: Expression, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addImplementationDependency(dependency, sourceSet) }
  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addRuntimeOnlyDependency(dependency, sourceSet) }
  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?): BuildScriptDataBuilder = apply { backend.addRuntimeOnlyDependency(dependency, sourceSet) }
  override fun addTestImplementationDependency(dependency: String): BuildScriptDataBuilder = apply { backend.addTestImplementationDependency(dependency) }
  override fun addTestImplementationDependency(dependency: Expression): BuildScriptDataBuilder = apply { backend.addTestImplementationDependency(dependency) }
  override fun addTestRuntimeOnlyDependency(dependency: String): BuildScriptDataBuilder = apply { backend.addTestRuntimeOnlyDependency(dependency) }
  override fun addTestRuntimeOnlyDependency(dependency: Expression): BuildScriptDataBuilder = apply { backend.addTestRuntimeOnlyDependency(dependency) }
  override fun addBuildScriptClasspath(dependency: String): BuildScriptDataBuilder = apply { backend.addBuildScriptClasspath(dependency) }
  override fun addBuildScriptClasspath(dependency: Expression): BuildScriptDataBuilder = apply { backend.addBuildScriptClasspath(dependency) }
  override fun withMavenCentral(): BuildScriptDataBuilder = apply { backend.withMavenCentral() }
  override fun withBuildScriptMavenCentral(): BuildScriptDataBuilder = apply { backend.withBuildScriptMavenCentral() }
  override fun applyPlugin(plugin: String): BuildScriptDataBuilder = apply { backend.applyPlugin(plugin) }
  override fun applyPluginFrom(path: String): BuildScriptDataBuilder = apply { backend.applyPluginFrom(path) }
  override fun withPlugin(id: String, version: String?): BuildScriptDataBuilder = apply { backend.withPlugin(id, version) }
  override fun withJavaPlugin(): BuildScriptDataBuilder = apply { backend.withJavaPlugin() }
  override fun withJavaLibraryPlugin(): BuildScriptDataBuilder = apply { backend.withJavaLibraryPlugin() }
  override fun withIdeaPlugin(): BuildScriptDataBuilder = apply { backend.withIdeaPlugin() }
  override fun withKotlinJvmPlugin(): BuildScriptDataBuilder = apply { backend.withKotlinJvmPlugin() }
  override fun withKotlinJvmPlugin(version: String?): BuildScriptDataBuilder = apply { backend.withKotlinJvmPlugin(version) }
  override fun withKotlinJsPlugin(): BuildScriptDataBuilder = apply { backend.withKotlinJsPlugin() }
  override fun withKotlinMultiplatformPlugin(): BuildScriptDataBuilder = apply { backend.withKotlinMultiplatformPlugin() }
  override fun withKotlinDsl(): BuildScriptDataBuilder = apply { backend.withKotlinDsl() }
  override fun withKotlinJvmToolchain(jvmTarget: Int): BuildScriptDataBuilder = apply { backend.withKotlinJvmToolchain(jvmTarget) }
  override fun withGroovyPlugin(): BuildScriptDataBuilder = apply { backend.withGroovyPlugin() }
  override fun withGroovyPlugin(version: String): BuildScriptDataBuilder = apply { backend.withGroovyPlugin(version) }
  override fun withApplicationPlugin(mainClass: String?, mainModule: String?, executableDir: String?, defaultJvmArgs: List<String>?): BuildScriptDataBuilder =
    apply { backend.withApplicationPlugin(mainClass, mainModule, executableDir, defaultJvmArgs) }
  override fun withKotlinTest(): BuildScriptDataBuilder = apply { backend.withKotlinTest() }
  override fun withJUnit(): BuildScriptDataBuilder = apply { backend.withJUnit() }
  override fun withJUnit4(): BuildScriptDataBuilder = apply { backend.withJUnit4() }
  override fun withJUnit5(): BuildScriptDataBuilder = apply { backend.withJUnit5() }
  override fun withJava(configure: ScriptTreeBuilder.() -> Unit): BuildScriptDataBuilder = apply { backend.withJava(configure) }
  override fun withJava(configure: Consumer<ScriptTreeBuilder>): BuildScriptDataBuilder = apply { backend.withJava(configure) }
  override fun targetCompatibility(level: String): BuildScriptDataBuilder = apply { backend.targetCompatibility(level) }
  override fun sourceCompatibility(level: String): BuildScriptDataBuilder = apply { backend.sourceCompatibility(level) }

  override fun project(name: String): Expression = backend.project(name)
  override fun project(name: String, configuration: String): Expression = backend.project(name, configuration)
  override fun ScriptTreeBuilder.mavenRepository(url: String): ScriptTreeBuilder = with(backend) { mavenRepository(url) }
  override fun ScriptTreeBuilder.mavenCentral(): ScriptTreeBuilder = with(backend) { mavenCentral() }
  // @formatter:on

  companion object {
    private val LOG = Logger.getInstance(BuildScriptDataBuilder::class.java)
  }
}