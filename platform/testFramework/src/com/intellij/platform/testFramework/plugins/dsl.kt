// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.plugins

import com.intellij.ide.plugins.ModuleLoadingRule
import org.intellij.lang.annotations.Language
import java.util.concurrent.atomic.AtomicInteger

private val idCounter = AtomicInteger()
private fun autoId(): String = "plugin_${idCounter.incrementAndGet()}"

fun plugin(id: String? = autoId(), body: PluginSpecBuilder.() -> Unit): PluginSpec {
  val builder = PluginSpecBuilder()
  builder.id = id
  builder.body()
  return builder.build()
}

fun PluginSpecBuilder.depends(pluginId: String) {
  pluginDependencies += DependsSpec(pluginId, null, null)
}

fun PluginSpecBuilder.depends(pluginId: String, configFile: String, configBody: PluginSpecBuilder.() -> Unit) {
  val dependsDesc = PluginSpecBuilder()
  dependsDesc.configBody()
  pluginDependencies += DependsSpec(pluginId, configFile, dependsDesc.build())
}

class DependenciesScope(internal val plugin: PluginSpecBuilder)

fun PluginSpecBuilder.dependencies(body: DependenciesScope.() -> Unit) {
  val scope = DependenciesScope(this)
  scope.body()
}

fun DependenciesScope.plugin(id: String) {
  plugin.pluginMainModuleDependencies += id
}

fun DependenciesScope.module(name: String) {
  plugin.moduleDependencies += name
}

class ContentScope(internal val plugin: PluginSpecBuilder)

fun PluginSpecBuilder.content(body: ContentScope.() -> Unit) {
  val scope = ContentScope(this)
  scope.body()
}

fun ContentScope.module(moduleName: String, loadingRule: ModuleLoadingRule = ModuleLoadingRule.OPTIONAL, body: PluginSpecBuilder.() -> Unit) {
  val moduleBuilder = PluginSpecBuilder()
  moduleBuilder.body()
  plugin.content += ContentModuleSpec(moduleName, loadingRule, moduleBuilder.build())
}

fun PluginSpecBuilder.extensions(@Language("XML") xml: String, ns: String = "com.intellij") {
  extensions += ExtensionsSpec(ns, xml)
}

fun PluginSpecBuilder.action(classFqn: String, id: String = classFqn) {
  actions += """<action id="$id" class="$classFqn" />"""
}

inline fun <reified T> PluginSpecBuilder.action(id: String = T::class.java.name): Unit = action(T::class.java.name, id)

fun PluginSpecBuilder.appService(classFqn: String) {
  extensions("""<applicationService serviceImplementation="${classFqn}" />""")
}

inline fun <reified T> PluginSpecBuilder.appService(): Unit = appService(T::class.java.name)

fun PluginSpecBuilder.pluginAlias(id: String) {
  pluginAliases += id
}

inline fun <reified T> PluginSpecBuilder.includeClassFile(): Unit = includeClassFile(T::class.java.name)

fun PluginSpecBuilder.includeClassFile(classFqn: String) {
  classFiles += classFqn
}

inline fun <reified T> PluginSpecBuilder.includePackageClassFiles(): Unit = includePackageClassFiles(T::class.java.packageName)

fun PluginSpecBuilder.includePackageClassFiles(packageFqn: String) {
  packageClassFiles += packageFqn
}

fun PluginSpecBuilder.dependsIntellijModulesLang(): Unit = depends("com.intellij.modules.lang")