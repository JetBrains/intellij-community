// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.FusCompletionKeys.LOOKUP_ELEMENT_CONTRIBUTOR
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass as kotlinContributorClass

@ApiStatus.Internal
class KotlinGradleCleanupCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, insideScriptBlockPattern(DEPENDENCIES), RemainingCompletionContributorsFilterer())
  }

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): @Nls String? {
    if (parameters.invocationCount != 1) return null
    if (!isGradleDependenciesCompletionEnabled(parameters)) return null
    if (!isCaseCoveredByGradleCompletion(parameters.position)) return null
    val shortcut = KeymapUtil.getFirstKeyboardShortcutText("CodeCompletion")
    return GradleBundle.message("gradle.dependency.completion.no.suggestions.first.invocation", shortcut)
  }
}

private class RemainingCompletionContributorsFilterer : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (!isGradleDependenciesCompletionEnabled(parameters)) return
    // don't ignore other contributors in cases when Gradle completion doesn't suggest anything
    if (!isCaseCoveredByGradleCompletion(parameters.position)) return

    if (parameters.invocationCount >= 2) return
    // Hide ignored contributors for autocompletion (0 invocations) and first-time invoked completion

    val sortedResult = prioritizeGradleCompletion(result)
    sortedResult.runRemainingContributors(parameters) { otherResult ->
      if (isContributorIgnored(otherResult)) return@runRemainingContributors
      sortedResult.passResult(otherResult)
    }
  }
}

private fun isCaseCoveredByGradleCompletion(element: PsiElement): Boolean {
  return element.isOnTheTopLevelOfScriptBlock(DEPENDENCIES)
         || element.isDependencyArgumentInsideQuotes()
         || element.isDependencyArgumentWithoutQuotes()
         || element.isExcludeArgument()
}

private fun prioritizeGradleCompletion(result: CompletionResultSet): CompletionResultSet {
  val weigher = object : LookupElementWeigher("gradle.completion") {
    override fun weigh(element: LookupElement): Comparable<Int> {
      val clazz = getContributorClass(element)
      if (clazz != KotlinGradleScriptCompletionContributor::class.java) {
        return Int.MAX_VALUE // put on bottom results from non-Gradle contributors
      }
      return 0
    }
  }
  return result.withRelevanceSorter(CompletionSorter.emptySorter().weigh(weigher))
}

private fun isContributorIgnored(otherResult: CompletionResult): Boolean {
  val contributorClass = getContributorClass(otherResult.lookupElement) ?: return false
  return contributorClass.name in ignoredContributors
         || isProducedByK2Contributor(otherResult.lookupElement)
}

private fun isProducedByK2Contributor(lookupElement: LookupElement): Boolean {
  val contributorClass = lookupElement.kotlinContributorClass ?: return false
  return generateSequence(contributorClass) { it.superclass }
    .any { it.name == "org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionContributor" }
}

private val ignoredContributors = setOf(
  "com.intellij.codeInsight.completion.LegacyCompletionContributor",          // `java`, `javax`, `jdk`, `META-INF`, etc.
  "com.intellij.lang.properties.references.PropertiesCompletionContributor",  // properties from */src/main/resources/*.properties file
  "com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor", // interface, ifn, fun, etc.
  "org.jetbrains.kotlin.idea.completion.implCommon.KotlinDumbCompletionContributor", // keywords in dumb mode
)

private fun getContributorClass(lookupElement: LookupElement): Class<CompletionContributor>? =
  lookupElement.getUserData(LOOKUP_ELEMENT_CONTRIBUTOR)?.javaClass
