// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.*
import java.time.LocalDate

interface PluginDescriptorBuilder {
  var id: String?
  var name: String?

  var description: String?
  var category: String?
  var changeNotes: String?

  var version: String?
  var sinceBuild: String?
  @Deprecated("Deprecated since 2025.2, the value is disregarded if its major part is at least 251. " +
              "Nonetheless, IDE consults since-until constraints taken directly from the Marketplace, so they can be set there if you need it.")
  var untilBuild: String?

  var `package`: String?
  var isSeparateJar: Boolean

  var url: String?
  var vendor: String?
  var vendorEmail: String?
  var vendorUrl: String?

  var resourceBundleBaseName: String?

  var isUseIdeaClassLoader: Boolean
  var isBundledUpdateAllowed: Boolean
  var isImplementationDetail: Boolean
  var isRestartRequired: Boolean
  var isLicenseOptional: Boolean
  var isIndependentFromCoreClassLoader: Boolean

  var productCode: String?
  var releaseDate: LocalDate?
  var releaseVersion: Int

  fun addPluginAlias(alias: String)
  val pluginAliases: List<String>

  fun addDepends(depends: DependsElement)
  val depends: List<DependsElement>

  fun addAction(action: ActionElement)
  val actions: List<ActionElement>

  fun addIncompatibleWith(incompatibleWith: String)
  val incompatibleWith: List<String>

  val appContainerBuilder: ScopedElementsContainerBuilder
  val projectContainerBuilder: ScopedElementsContainerBuilder
  val moduleContainerBuilder: ScopedElementsContainerBuilder

  fun addExtension(qualifiedExtensionPointName: String, extension: ExtensionElement)
  val extensions: Map<String, List<ExtensionElement>>

  fun addContentModule(contentModule: ContentElement)
  val contentModules: List<ContentElement>

  fun addDependency(dependency: DependenciesElement)
  val dependencies: List<DependenciesElement>

  fun build(): RawPluginDescriptor

  companion object {
    fun builder(): PluginDescriptorBuilder = PluginDescriptorBuilderImpl()
  }
}