// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")

package com.intellij.platform.testFramework.plugins

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.elements.ModuleVisibilityValue
import org.intellij.lang.annotations.Language


class PluginSpec internal constructor(
  val id: String?,
  val name: String?,
  val packagePrefix: String?,
  val implementationDetail: Boolean,
  val isSeparateJar: Boolean,
  val rootTagAttributes: String?,

  val untilBuild: String?,
  val strictUntilBuild: String?,
  val sinceBuild: String?,
  val category: String?,
  val version: String?,
  val vendor: String?,
  val description: String?,

  val pluginDependencies: List<DependsSpec>,
  val moduleDependencies: List<ModuleDependencySpec>,
  val pluginMainModuleDependencies: List<String>,
  val moduleVisibility: ModuleVisibilityValue,

  val pluginAliases: List<String>,
  val incompatibleWith: List<String>,
  val namespace: String?,
  val content: List<ContentModuleSpec>,

  val resourceBundle: String?,
  val actions: String?,
  val applicationListeners: String?,
  val extensionPoints: String?,
  val extensions: List<ExtensionsSpec>,

  val body: String?,

  val classFiles: List<PluginSpecClassReference>,
  val packageClassFiles: List<PluginSpecClassReference>,
)

data class PluginSpecClassReference(val className: String, val classLoader: ClassLoader? = null)

class PluginSpecBuilder(
  var id: String? = null,
  var name: String? = null,
  var packagePrefix: String? = null,
  var implementationDetail: Boolean = false,
  var isSeparateJar: Boolean = false,
  var rootTagAttributes: String? = null,

  var untilBuild: String? = null,
  var strictUntilBuild: String? = null,
  var sinceBuild: String? = null,
  var category: String? = null,
  var version: String? = null,
  var vendor: String? = null,
  var description: String? = null,

  internal var pluginDependencies: List<DependsSpec> = emptyList(),
  internal var moduleDependencies: List<ModuleDependencySpec> = emptyList(),
  internal var pluginMainModuleDependencies: List<String> = emptyList(),

  var moduleVisibility: ModuleVisibilityValue = ModuleVisibilityValue.PRIVATE,
  var pluginAliases: List<String> = emptyList(),
  var incompatibleWith: List<String> = emptyList(),
  var namespace: String? = null,
  internal var content: List<ContentModuleSpec> = emptyList(),

  var resourceBundle: String? = null,
  @Language("XML") var actions: String? = null,
  @Language("XML") var applicationListeners: String? = null,
  @Language("XML") var extensionPoints: String? = null,
  internal var extensions: List<ExtensionsSpec> = emptyList(),

  @Language("XML") var body: String? = null,

  internal var classFiles: List<PluginSpecClassReference> = emptyList(),
  internal var packageClassFiles: List<PluginSpecClassReference> = emptyList(),
) {
  fun build(): PluginSpec = PluginSpec(
    id = id, name = name, packagePrefix = packagePrefix, implementationDetail = implementationDetail, isSeparateJar = isSeparateJar,
    rootTagAttributes = rootTagAttributes,
    untilBuild = untilBuild, strictUntilBuild = strictUntilBuild, sinceBuild = sinceBuild,
    category = category, version = version,
    vendor = vendor, description = description, pluginDependencies = pluginDependencies, moduleDependencies = moduleDependencies,
    moduleVisibility = moduleVisibility,
    pluginMainModuleDependencies = pluginMainModuleDependencies, pluginAliases = pluginAliases, incompatibleWith = incompatibleWith,
    namespace = namespace,
    content = content, resourceBundle = resourceBundle, actions = actions, applicationListeners = applicationListeners,
    extensionPoints = extensionPoints, extensions = extensions, body = body, classFiles = classFiles, packageClassFiles = packageClassFiles
  )
}

class ContentModuleSpec internal constructor(
  val moduleId: String,
  val loadingRule: ModuleLoadingRuleValue,
  val requiredIfAvailable: String?,
  val spec: PluginSpec,
)

class DependsSpec internal constructor(val pluginId: String, val optional: Boolean, val configFile: String?, val spec: PluginSpec?) {
  init { require((configFile != null) == (spec != null)) }
}

class ModuleDependencySpec internal constructor(val name: String, val namespace: String? = null)

class ExtensionsSpec internal constructor(val ns: String, @Language("XML") val content: String)
