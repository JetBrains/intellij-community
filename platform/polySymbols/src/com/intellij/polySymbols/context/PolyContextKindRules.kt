// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context

import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.impl.PolyContextKindRulesBuilderImpl
import com.intellij.polySymbols.context.impl.PolyContextKindRulesImpl
import org.jetbrains.annotations.ApiStatus

interface PolyContextKindRules {

  val enable: Map<PolyContextName, List<EnablementRules>>
  val disable: Map<PolyContextName, List<DisablementRules>>

  @ApiStatus.NonExtendable
  interface DisablementRules {
    val fileExtensions: List<String>
    val fileNamePatterns: List<Regex>
  }

  @ApiStatus.NonExtendable
  interface EnablementRules {
    val pkgManagerDependencies: Map<String, List<String>>
    val projectToolExecutables: List<String>
    val fileExtensions: List<String>
    val ideLibraries: List<String>
    val fileNamePatterns: List<Regex>
  }

  @ApiStatus.NonExtendable
  interface Builder {
    fun contextName(name: PolyContextName, builder: ContextKindBuilder.() -> Unit)
  }

  @ApiStatus.NonExtendable
  interface ContextKindBuilder {
    fun enabledWhen(builder: EnablementRulesBuilder.() -> Unit)
    fun disableWhen(builder: DisablementRulesBuilder.() -> Unit)
  }

  @ApiStatus.NonExtendable
  interface DisablementRulesBuilder {
    fun fileExtensions(fileExtensions: List<String>): DisablementRulesBuilder
    fun fileNamePatterns(filenamePatterns: List<Regex>): DisablementRulesBuilder
    fun build(): DisablementRules
  }

  @ApiStatus.NonExtendable
  interface EnablementRulesBuilder {
    fun fileExtensions(fileExtensions: List<String>): EnablementRulesBuilder
    fun fileNamePatterns(filenamePatterns: List<Regex>): EnablementRulesBuilder
    fun ideLibraries(ideLibraries: List<String>): EnablementRulesBuilder
    fun projectToolExecutables(projectToolExecutables: List<String>): EnablementRulesBuilder
    fun pkgManagerDependencies(packageManager: String, dependencies: List<String>): EnablementRulesBuilder
    fun pkgManagerDependencies(dependencies: Map<String, List<String>>): EnablementRulesBuilder
    fun build(): EnablementRules
  }

  companion object {

    @JvmStatic
    fun create(
      enable: Map<PolyContextName, List<EnablementRules>>,
      disable: Map<PolyContextName, List<DisablementRules>>,
    ): PolyContextKindRules =
      PolyContextKindRulesImpl(enable, disable)


    @JvmStatic
    fun create(builder: Builder.() -> Unit): PolyContextKindRules =
      PolyContextKindRulesBuilderImpl().apply(builder).build()

    @JvmStatic
    fun createEnablementRules(builder: EnablementRulesBuilder.() -> Unit): EnablementRules =
      PolyContextKindRulesBuilderImpl.EnablementRulesBuilderImpl().apply(builder).build()

    @JvmStatic
    fun createDisablementRules(builder: DisablementRulesBuilder.() -> Unit): DisablementRules =
      PolyContextKindRulesBuilderImpl.DisablementRulesBuilderImpl().apply(builder).build()

    @JvmStatic
    fun enablementRulesBuilder(): EnablementRulesBuilder =
      PolyContextKindRulesBuilderImpl.EnablementRulesBuilderImpl()

    @JvmStatic
    fun disablementRulesBuilder(): DisablementRulesBuilder =
      PolyContextKindRulesBuilderImpl.DisablementRulesBuilderImpl()
  }

}
