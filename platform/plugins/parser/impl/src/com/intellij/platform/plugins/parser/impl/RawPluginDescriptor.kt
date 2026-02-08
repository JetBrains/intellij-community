// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import com.intellij.platform.plugins.parser.impl.elements.ContentModuleElement
import com.intellij.platform.plugins.parser.impl.elements.DependenciesElement
import com.intellij.platform.plugins.parser.impl.elements.DependsElement
import com.intellij.platform.plugins.parser.impl.elements.ExtensionElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleVisibilityValue
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
  /**
   * 'until-build' attribute will be deprecated and ignored in future IDE versions.
   * 'strict-until-build' ([strictUntilBuild]) will be used instead.
   */
  val untilBuild: String?,
  val strictUntilBuild: String?,

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

  /** Specifies namespace for content modules of the plugin */
  val namespace: String?,

  val contentModules: List<ContentModuleElement>,

  /** Specifies the visibility of this content module. Irrelevant for a main plugin descriptor or config-file in a `<depends>` tag */
  val moduleVisibility: ModuleVisibilityValue,

  val dependencies: List<DependenciesElement>,
)
