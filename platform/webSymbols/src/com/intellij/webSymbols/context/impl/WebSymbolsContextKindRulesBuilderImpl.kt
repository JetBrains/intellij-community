// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.util.SmartList
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import java.util.Collections.unmodifiableList

class WebSymbolsContextKindRulesBuilderImpl : WebSymbolsContextKindRules.Builder {

  private val contextNames = mutableMapOf<ContextName, ContextKindBuilderImpl>()

  override fun contextName(name: ContextName, builder: WebSymbolsContextKindRules.ContextKindBuilder.() -> Unit) {
    contextNames.computeIfAbsent(name) { ContextKindBuilderImpl() }.apply(builder)
  }

  fun build(): WebSymbolsContextKindRules =
    WebSymbolsContextKindRulesImpl(
      contextNames.mapValues { listOf(it.value.enableWhen.build()) },
      contextNames.mapValues { listOf(it.value.disableWhen.build()) },
    )

  private class ContextKindBuilderImpl : WebSymbolsContextKindRules.ContextKindBuilder {
    val enableWhen = EnablementRulesBuilderImpl()
    val disableWhen = DisablementRulesBuilderImpl()

    override fun enabledWhen(builder: WebSymbolsContextKindRules.EnablementRulesBuilder.() -> Unit) {
      enableWhen.apply(builder)
    }

    override fun disableWhen(builder: WebSymbolsContextKindRules.DisablementRulesBuilder.() -> Unit) {
      disableWhen.apply(builder)
    }

  }

  class EnablementRulesBuilderImpl : WebSymbolsContextKindRules.EnablementRulesBuilder {
    private val fileExtensions = SmartList<String>()
    private val fileNamePatterns = SmartList<Regex>()
    private val ideLibraries = SmartList<String>()
    private val projectToolExecutables = SmartList<String>()
    private val pkgManagerDependencies = HashMap<String, MutableList<String>>()

    override fun fileExtensions(fileExtensions: List<String>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      this.fileExtensions.addAll(fileExtensions)
    }

    override fun fileNamePatterns(filenamePatterns: List<Regex>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      this.fileNamePatterns.addAll(filenamePatterns)
    }

    override fun ideLibraries(ideLibraries: List<String>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      this.ideLibraries.addAll(ideLibraries)
    }

    override fun projectToolExecutables(projectToolExecutables: List<String>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      this.projectToolExecutables.addAll(projectToolExecutables)
    }

    override fun pkgManagerDependencies(packageManager: String, dependencies: List<String>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      pkgManagerDependencies.computeIfAbsent(packageManager) { SmartList() }.addAll(dependencies)
    }

    override fun pkgManagerDependencies(dependencies: Map<String, List<String>>): WebSymbolsContextKindRules.EnablementRulesBuilder = apply {
      dependencies.forEach {
        pkgManagerDependencies(it.key, it.value)
      }
    }

    override fun build(): WebSymbolsContextKindRules.EnablementRules = EnablementRulesData(
      pkgManagerDependencies = pkgManagerDependencies.mapValues { unmodifiableList(it.value) },
      projectToolExecutables = unmodifiableList(projectToolExecutables),
      fileExtensions = unmodifiableList(fileExtensions),
      ideLibraries = unmodifiableList(ideLibraries),
      fileNamePatterns = unmodifiableList(fileNamePatterns),
    )

  }

  class DisablementRulesBuilderImpl : WebSymbolsContextKindRules.DisablementRulesBuilder {
    private val fileExtensions = SmartList<String>()
    private val fileNamePatterns = SmartList<Regex>()

    override fun fileExtensions(fileExtensions: List<String>): WebSymbolsContextKindRules.DisablementRulesBuilder = apply {
      this.fileExtensions.addAll(fileExtensions)
    }

    override fun fileNamePatterns(filenamePatterns: List<Regex>): WebSymbolsContextKindRules.DisablementRulesBuilder = apply {
      this.fileNamePatterns.addAll(filenamePatterns)
    }

    override fun build(): WebSymbolsContextKindRules.DisablementRules = DisablementRulesData(
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
  override val fileNamePatterns: List<Regex>
) : WebSymbolsContextKindRules.EnablementRules

private data class DisablementRulesData(
  override val fileExtensions: List<String>,
  override val fileNamePatterns: List<Regex>
) : WebSymbolsContextKindRules.DisablementRules
