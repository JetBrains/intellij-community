// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.completion.FullStringInsertHandler
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.gradle.completion.GradleDependencyCompletionFuzzyMatcher
import com.intellij.gradle.completion.getCompletionContext
import com.intellij.gradle.completion.removeDummySuffix
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.statistics.BT_COMPLETION_IS_AUTO_POPUP
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService
import org.toml.lang.psi.TomlLiteral

private const val moduleKey = "module"
private const val groupKey = "group"
private const val artifactKey = "name"
private const val versionKey = "version"

internal class GradleTomlCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    if (!useDependencyCompletionService()) {
      return
    }

    val positionElement = parameters.position
    val tomlLiteral = positionElement.parent as? TomlLiteral ?: return

    val dummyText = positionElement.text.removeWrappingQuotes()
    val text = removeDummySuffix(dummyText)
    val tomlKey = tomlLiteral.getTomlKey() ?: return
    val key = tomlKey.getLastSegmentName()

    val completionService = service<DependencyCompletionService>()

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))

    when {
      key == moduleKey -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect {
              resultSet.addElement(
                it,
                it.groupId + ":" + it.artifactId,
                GradleTomlLibraryCompletionPosition.MODULE,
                parameters.isAutoPopup,
              )
            }
        }
      }

      key == groupKey -> {
        val artifact = tomlLiteral.getSiblingValue(artifactKey)
        val request = DependencyGroupCompletionRequest(text, artifact, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestGroupCompletions(request)
            .collect { resultSet.addElement(it, it.result, GradleTomlLibraryCompletionPosition.GROUP, parameters.isAutoPopup) }
        }
      }

      key == artifactKey -> {
        val group = tomlLiteral.getSiblingValue(groupKey)
        val request = DependencyArtifactCompletionRequest(group, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestArtifactCompletions(request)
            .collect { resultSet.addElement(it, it.result, GradleTomlLibraryCompletionPosition.ARTIFACT, parameters.isAutoPopup) }
        }
      }

      key == versionKey -> {
        val (group, artifact) = tomlLiteral.getGroupAndArtifactForVersion()
        val request = DependencyVersionCompletionRequest(group, artifact, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestVersionCompletions(request)
            .collect { resultSet.addElement(it, it.result, GradleTomlLibraryCompletionPosition.VERSION, parameters.isAutoPopup) }
        }
      }

      // must be at the end of when
      tomlKey.isDirectlyInLibrariesTable() -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect {
              resultSet.addElement(
                it,
                it.groupId + ":" + it.artifactId + ":" + it.version,
                GradleTomlLibraryCompletionPosition.GAV,
                parameters.isAutoPopup,
              )
            }
        }
      }

    }
  }

  private fun CompletionResultSet.addElement(
    lookupObject: Any,
    lookupString: String,
    pos: GradleTomlLibraryCompletionPosition,
    isAutoPopup: Boolean,
  ) {
    val lookupElement = LookupElementBuilder
      .create(lookupObject, lookupString)
      .withPresentableText(lookupString)
      .withInsertHandler(FullStringInsertHandler)
    lookupElement.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)
    lookupElement.putUserData(BT_COMPLETION_IS_AUTO_POPUP, isAutoPopup)
    lookupElement.putUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY, pos)
    lookupElement.putUserData(SUPPRESS_QUICK_DEFINITION, true)
    lookupElement.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)

    this.addElement(lookupElement)
  }

  private fun TomlLiteral.getGroupAndArtifactForVersion(): Pair<String, String> {
    val module = getSiblingValue(moduleKey)
    if (module.isNotEmpty()) return module.substringBefore(':') to module.substringAfter(':')

    val group = getSiblingValue(groupKey)
    val artifact = getSiblingValue(artifactKey)
    return group to artifact
  }
}