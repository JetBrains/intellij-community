// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.completion.FullStringInsertHandler
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.gradle.completion.GradleDependencyCompletionMatcher
import com.intellij.gradle.completion.getCompletionContext
import com.intellij.gradle.completion.removeDummySuffix
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.completion.api.DependencyArtifactCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionService
import org.jetbrains.idea.completion.api.DependencyGroupCompletionRequest
import org.jetbrains.idea.completion.api.DependencyVersionCompletionRequest
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

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionMatcher(text))

    when {
      key == moduleKey -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect { resultSet.addElement(it.groupId + ":" + it.artifactId) }
        }
      }

      key == groupKey -> {
        val artifact = tomlLiteral.getSiblingValue(artifactKey)
        val request = DependencyGroupCompletionRequest(text, artifact, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestGroupCompletions(request)
            .collect { resultSet.addElement(it) }
        }
      }

      key == artifactKey -> {
        val group = tomlLiteral.getSiblingValue(groupKey)
        val request = DependencyArtifactCompletionRequest(group, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestArtifactCompletions(request)
            .collect { resultSet.addElement(it) }
        }
      }

      key == versionKey -> {
        val (group, artifact) = tomlLiteral.getGroupAndArtifactForVersion()
        val request = DependencyVersionCompletionRequest(group, artifact, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestVersionCompletions(request)
            .collect { resultSet.addElement(it) }
        }
      }

      // must be at the end of when
      tomlKey.isDirectlyInLibrariesTable() -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect { resultSet.addElement(it.groupId + ":" + it.artifactId + ":" + it.version) }
        }
      }

    }
  }

  private fun CompletionResultSet.addElement(lookupString: String) {
    val lookupObject = "\"$lookupString\""
    val lookupElement = LookupElementBuilder
      .create(lookupObject, lookupString)
      .withPresentableText(lookupString)
      .withInsertHandler(FullStringInsertHandler)
    lookupElement.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)

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