// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context.impl

import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.util.SmartList
import java.util.Collections.unmodifiableList

class PolyContextKindRulesBuilderImpl : PolyContextKindRules.Builder {

  private val contextNames = mutableMapOf<PolyContextName, ContextKindBuilderImpl>()

  override fun contextName(name: PolyContextName, builder: PolyContextKindRules.ContextKindBuilder.() -> Unit) {
    contextNames.computeIfAbsent(name) { ContextKindBuilderImpl() }.apply(builder)
  }

  fun build(): PolyContextKindRules =
    PolyContextKindRulesImpl(
      contextNames.mapValues { listOf(it.value.enableWhen.build()) },
      contextNames.mapValues { listOf(it.value.disableWhen.build()) },
    )

  private class ContextKindBuilderImpl : PolyContextKindRules.ContextKindBuilder {
    val enableWhen = EnablementRulesBuilderImpl()
    val disableWhen = DisablementRulesBuilderImpl()

    override fun enabledWhen(builder: PolyContextKindRules.EnablementRulesBuilder.() -> Unit) {
      enableWhen.apply(builder)
    }

    override fun disableWhen(builder: PolyContextKindRules.DisablementRulesBuilder.() -> Unit) {
      disableWhen.apply(builder)
    }

  }

  class EnablementRulesBuilderImpl : PolyContextKindRules.EnablementRulesBuilder {
    private val fileExtensions = SmartList<String>()
    private val fileNamePatterns = SmartList<Regex>()
    private val ideLibraries = SmartList<String>()
    private val projectToolExecutables = SmartList<String>()
    private val pkgManagerDependencies = HashMap<String, MutableList<String>>()

    override fun fileExtensions(fileExtensions: List<String>): PolyContextKindRules.EnablementRulesBuilder = apply {
      this.fileExtensions.addAll(fileExtensions)
    }

    override fun fileNamePatterns(filenamePatterns: List<Regex>): PolyContextKindRules.EnablementRulesBuilder = apply {
      this.fileNamePatterns.addAll(filenamePatterns)
    }

    override fun ideLibraries(ideLibraries: List<String>): PolyContextKindRules.EnablementRulesBuilder = apply {
      this.ideLibraries.addAll(ideLibraries)
    }

    override fun projectToolExecutables(projectToolExecutables: List<String>): PolyContextKindRules.EnablementRulesBuilder = apply {
      this.projectToolExecutables.addAll(projectToolExecutables)
    }

    override fun pkgManagerDependencies(packageManager: String, dependencies: List<String>): PolyContextKindRules.EnablementRulesBuilder = apply {
      pkgManagerDependencies.computeIfAbsent(packageManager) { SmartList() }.addAll(dependencies)
    }

    override fun pkgManagerDependencies(dependencies: Map<String, List<String>>): PolyContextKindRules.EnablementRulesBuilder = apply {
      dependencies.forEach {
        pkgManagerDependencies(it.key, it.value)
      }
    }

    override fun build(): PolyContextKindRules.EnablementRules = EnablementRulesData(
      pkgManagerDependencies = pkgManagerDependencies.mapValues { unmodifiableList(it.value) },
      projectToolExecutables = unmodifiableList(projectToolExecutables),
      fileExtensions = unmodifiableList(fileExtensions),
      ideLibraries = unmodifiableList(ideLibraries),
      fileNamePatterns = unmodifiableList(fileNamePatterns),
    )

  }

  class DisablementRulesBuilderImpl : PolyContextKindRules.DisablementRulesBuilder {
    private val fileExtensions = SmartList<String>()
    private val fileNamePatterns = SmartList<Regex>()

    override fun fileExtensions(fileExtensions: List<String>): PolyContextKindRules.DisablementRulesBuilder = apply {
      this.fileExtensions.addAll(fileExtensions)
    }

    override fun fileNamePatterns(filenamePatterns: List<Regex>): PolyContextKindRules.DisablementRulesBuilder = apply {
      this.fileNamePatterns.addAll(filenamePatterns)
    }

    override fun build(): PolyContextKindRules.DisablementRules = DisablementRulesData(
      fileExtensions = unmodifiableList(fileExtensions),
      fileNamePatterns = unmodifiableList(fileNamePatterns),
    )
  }
}

private data class EnablementRulesData(
  override val pkgManagerDependencies: Map<String, List<String>>,
  override val projectToolExecutables: List<String>,
  override val fileExtensions: List<String>,
  override val ideLibraries: List<String>,
  override val fileNamePatterns: List<Regex>,
) : PolyContextKindRules.EnablementRules

private data class DisablementRulesData(
  override val fileExtensions: List<String>,
  override val fileNamePatterns: List<Regex>,
) : PolyContextKindRules.DisablementRules
