// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.elements.*
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDate

@ApiStatus.Internal
class RawPluginDescriptor(
  val id: String?,
  val name: String?,
  val description: @NlsSafe String?,
  val category: String?,
  val changeNotes: String?,

  val version: String?,
  val sinceBuild: String?,
  @Deprecated("Deprecated since 2025.2, the value is disregarded if its major part is at least 251. " +
              "Nonetheless, IDE consults since-until constraints taken directly from the Marketplace, so they can be set there if you need it.")
  val untilBuild: String?,

  val `package`: String?,
  val isSeparateJar: Boolean,

  val url: String?,
  val vendor: String?,
  val vendorEmail: String?,
  val vendorUrl: String?,

  val resourceBundleBaseName: String?,

  val isUseIdeaClassLoader: Boolean,
  val isBundledUpdateAllowed: Boolean,
  val isImplementationDetail: Boolean,
  val isRestartRequired: Boolean,
  val isLicenseOptional: Boolean,
  // makes sense only for product modules for now
  val isIndependentFromCoreClassLoader: Boolean,

  val productCode: String?,
  val releaseDate: LocalDate?,
  val releaseVersion: Int,

  val pluginAliases: List<String>,

  val depends: List<DependsElement>,
  val actions: List<ActionElement>,

  val incompatibleWith: List<String>,

  val appElementsContainer: ScopedElementsContainer,
  val projectElementsContainer: ScopedElementsContainer,
  val moduleElementsContainer: ScopedElementsContainer,
  /**
   * This map contains extensions with scope that cannot be determined immediately.
   * Key is extension point's FQN.
   * */
  val extensions: Map<String, List<ExtensionElement>>,

  val contentModules: List<ContentElement>,
  val dependencies: List<DependenciesElement>,
)
