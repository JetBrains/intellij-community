// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import com.intellij.platform.plugins.parser.impl.elements.ContentModuleElement
import com.intellij.platform.plugins.parser.impl.elements.DependenciesElement
import com.intellij.platform.plugins.parser.impl.elements.DependsElement
import com.intellij.platform.plugins.parser.impl.elements.ExtensionElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleVisibilityValue
import java.time.LocalDate

interface PluginDescriptorBuilder {
  var id: String?
  var name: String?

  var description: String?
  var category: String?
  var changeNotes: String?

  var version: String?
  var sinceBuild: String?
  var untilBuild: String?
  var strictUntilBuild: String?

  var `package`: String?
  var isSeparateJar: Boolean

  var visibility: ModuleVisibilityValue
  var namespace: String?

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

  fun addContentModule(contentModule: ContentModuleElement)
  val contentModules: List<ContentModuleElement>

  fun addDependency(dependency: DependenciesElement)
  val dependencies: List<DependenciesElement>

  fun build(): RawPluginDescriptor

  companion object {
    fun builder(): PluginDescriptorBuilder = PluginDescriptorBuilderImpl()
  }
}