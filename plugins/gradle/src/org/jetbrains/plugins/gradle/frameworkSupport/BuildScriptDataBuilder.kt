// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElementBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import java.util.function.Consumer


class BuildScriptDataBuilder(
  val buildScriptFile: VirtualFile,
  private val backend: GradleBuildScriptBuilder<*>
) : GradleBuildScriptBuilder<BuildScriptDataBuilder>, ScriptElementBuilder by backend {

  fun addBuildscriptPropertyDefinition(definition: String) =
    apply { addPrefix(definition.trim()) }

  fun addBuildscriptRepositoriesDefinition(definition: String) =
    apply { addBuildScriptRepository(definition.trim()) }

  fun addBuildscriptDependencyNotation(notation: String) =
    apply { addBuildScriptDependency(notation.trim()) }

  fun addPluginDefinitionInPluginsGroup(definition: String) =
    apply { addPlugin(definition.trim()) }

  fun addPluginDefinition(definition: String) =
    apply { addPrefix(definition.trim()) }

  fun addRepositoriesDefinition(definition: String) =
    apply { addRepository(definition.trim()) }

  fun addDependencyNotation(notation: String) = apply {
    if (notation.matches("\\s*(compile|testCompile|runtime|testRuntime)[^\\w].*".toRegex())) {
      LOG.warn(notation)
      LOG.warn("compile, testCompile, runtime and testRuntime dependency notations were deprecated in Gradle 3.4, " +
               "use implementation, api, compileOnly and runtimeOnly instead", Throwable())
    }
    addDependency(notation.trim())
  }

  fun addPropertyDefinition(definition: String) =
    apply { addPrefix(definition.trim()) }

  fun addOther(definition: String) =
    apply { addPostfix(definition.trim()) }

  // @formatter:off
  override val gradleVersion by backend::gradleVersion
  override fun addImport(import: String) = apply { backend.addImport(import) }
  override fun addBuildScriptPrefix(vararg prefix: String) = apply { backend.addBuildScriptPrefix(*prefix) }
  override fun withBuildScriptPrefix(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withBuildScriptPrefix(configure) }
  override fun withBuildScriptPrefix(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withBuildScriptPrefix(configure) }
  override fun addBuildScriptDependency(dependency: String) = apply { backend.addBuildScriptDependency(dependency) }
  override fun withBuildScriptDependency(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withBuildScriptDependency(configure) }
  override fun withBuildScriptDependency(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withBuildScriptDependency(configure) }
  override fun addBuildScriptRepository(repository: String) = apply { backend.addBuildScriptRepository(repository) }
  override fun withBuildScriptRepository(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withBuildScriptRepository(configure) }
  override fun withBuildScriptRepository(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withBuildScriptRepository(configure) }
  override fun addBuildScriptPostfix(vararg postfix: String) = apply { backend.addBuildScriptPostfix(*postfix) }
  override fun withBuildScriptPostfix(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withBuildScriptPostfix(configure) }
  override fun withBuildScriptPostfix(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withBuildScriptPostfix(configure) }
  override fun addPlugin(plugin: String) = apply { backend.addPlugin(plugin) }
  override fun withPlugin(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withPlugin(configure) }
  override fun addPrefix(vararg prefix: String) = apply { backend.addPrefix(*prefix) }
  override fun withPrefix(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withPrefix(configure) }
  override fun withPrefix(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withPrefix(configure) }
  override fun addDependency(dependency: String) = apply { backend.addDependency(dependency) }
  override fun withDependency(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withDependency(configure) }
  override fun withDependency(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withDependency(configure) }
  override fun addRepository(repository: String) = apply { backend.addRepository(repository) }
  override fun withRepository(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withRepository(configure) }
  override fun withRepository(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withRepository(configure) }
  override fun addPostfix(vararg postfix: String) = apply { backend.addPostfix(*postfix) }
  override fun withPostfix(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withPostfix(configure) }
  override fun withPostfix(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withPostfix(configure) }
  override fun generate() = backend.generate()
  override fun generateTree()= backend.generateTree()

  override fun addGroup(group: String) = apply { backend.addGroup(group) }
  override fun addVersion(version: String) = apply { backend.addVersion(version) }
  override fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.configureTestTask(configure) }
  override fun addDependency(scope: String, dependency: String, sourceSet: String?) = apply { backend.addDependency(scope, dependency, sourceSet) }
  override fun addDependency(scope: String, dependency: ScriptElement.Statement.Expression, sourceSet: String?) = apply { backend.addDependency(scope, dependency, sourceSet) }
  override fun addApiDependency(dependency: String, sourceSet: String?) = apply { backend.addApiDependency(dependency, sourceSet) }
  override fun addApiDependency(dependency: ScriptElement.Statement.Expression, sourceSet: String?) = apply { backend.addApiDependency(dependency, sourceSet) }
  override fun addCompileOnlyDependency(dependency: String, sourceSet: String?) = apply { backend.addCompileOnlyDependency(dependency, sourceSet) }
  override fun addCompileOnlyDependency(dependency: ScriptElement.Statement.Expression, sourceSet: String?) = apply { backend.addCompileOnlyDependency(dependency, sourceSet) }
  override fun addImplementationDependency(dependency: String, sourceSet: String?) = apply { backend.addImplementationDependency(dependency, sourceSet) }
  override fun addImplementationDependency(dependency: ScriptElement.Statement.Expression, sourceSet: String?) = apply { backend.addImplementationDependency(dependency, sourceSet) }
  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) = apply { backend.addRuntimeOnlyDependency(dependency, sourceSet) }
  override fun addRuntimeOnlyDependency(dependency: ScriptElement.Statement.Expression, sourceSet: String?) = apply { backend.addRuntimeOnlyDependency(dependency, sourceSet) }
  override fun addTestImplementationDependency(dependency: String) = apply { backend.addTestImplementationDependency(dependency) }
  override fun addTestImplementationDependency(dependency: ScriptElement.Statement.Expression) = apply { backend.addTestImplementationDependency(dependency) }
  override fun addTestRuntimeOnlyDependency(dependency: String) = apply { backend.addTestRuntimeOnlyDependency(dependency) }
  override fun addTestRuntimeOnlyDependency(dependency: ScriptElement.Statement.Expression) = apply { backend.addTestRuntimeOnlyDependency(dependency) }
  override fun addBuildScriptClasspath(dependency: String) = apply { backend.addBuildScriptClasspath(dependency) }
  override fun addBuildScriptClasspath(dependency: ScriptElement.Statement.Expression) = apply { backend.addBuildScriptClasspath(dependency) }
  override fun withMavenCentral() = apply { backend.withMavenCentral() }
  override fun withBuildScriptMavenCentral() = apply { backend.withBuildScriptMavenCentral() }
  override fun applyPlugin(plugin: String) = apply { backend.applyPlugin(plugin) }
  override fun applyPluginFrom(path: String) = apply { backend.applyPluginFrom(path) }
  override fun withPlugin(id: String, version: String?) = apply { backend.withPlugin(id, version) }
  override fun withJavaPlugin() = apply { backend.withJavaPlugin() }
  override fun withJavaLibraryPlugin() = apply { backend.withJavaLibraryPlugin() }
  override fun withIdeaPlugin() = apply { backend.withIdeaPlugin() }
  override fun withKotlinJvmPlugin() = apply { backend.withKotlinJvmPlugin() }
  override fun withKotlinJvmPlugin(version: String?): BuildScriptDataBuilder = apply { backend.withKotlinJvmPlugin(version) }
  override fun withKotlinJsPlugin() = apply { backend.withKotlinJsPlugin() }
  override fun withKotlinMultiplatformPlugin() = apply { backend.withKotlinMultiplatformPlugin() }
  override fun withKotlinDsl() = apply { backend.withKotlinDsl() }
  override fun withKotlinJvmToolchain(jvmTarget: Int): BuildScriptDataBuilder = apply { backend.withKotlinJvmToolchain(jvmTarget) }
  override fun withGroovyPlugin() = apply { backend.withGroovyPlugin() }
  override fun withGroovyPlugin(version: String) = apply { backend.withGroovyPlugin(version) }
  override fun withApplicationPlugin(mainClass: String?, mainModule: String?, executableDir: String?, defaultJvmArgs: List<String>?) =
    apply { backend.withApplicationPlugin(mainClass, mainModule, executableDir, defaultJvmArgs) }
  override fun withKotlinTest(): BuildScriptDataBuilder = apply { backend.withKotlinTest() }
  override fun withJUnit() = apply { backend.withJUnit() }
  override fun withJUnit4() = apply { backend.withJUnit4() }
  override fun withJUnit5() = apply { backend.withJUnit5() }
  override fun withJava(configure: ScriptTreeBuilder.() -> Unit) = apply { backend.withJava(configure) }
  override fun withJava(configure: Consumer<ScriptTreeBuilder>) = apply { backend.withJava(configure) }
  override fun targetCompatibility(level: String) = apply { backend.targetCompatibility(level) }
  override fun sourceCompatibility(level: String) = apply { backend.sourceCompatibility(level) }

  override fun project(name: String) = backend.project(name)
  override fun project(name: String, configuration: String) = backend.project(name, configuration)
  override fun ScriptTreeBuilder.mavenRepository(url: String) = with(backend) { mavenRepository(url) }
  override fun ScriptTreeBuilder.mavenCentral() = with(backend) { mavenCentral() }
  // @formatter:on

  companion object {
    private val LOG = Logger.getInstance(BuildScriptDataBuilder::class.java)
  }
}