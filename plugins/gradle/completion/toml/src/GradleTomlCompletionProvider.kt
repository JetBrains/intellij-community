// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.completion.DependencyCompletionLoadingAdvertiser
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.gradle.completion.GradleDependencyCompletionFuzzyMatcher
import com.intellij.gradle.completion.getCompletionContext
import com.intellij.gradle.completion.icon
import com.intellij.gradle.completion.removeDummySuffix
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiDocumentManager
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
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

    // Autocomplete the combined coordinate (module / direct GAV) only after 3 or more characters are typed
    if (parameters.isAutoPopup && text.length < 3 && (key == moduleKey || tomlKey.isDirectlyInLibrariesTable())) {
      return
    }

    val completionService = service<DependencyCompletionService>()

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))

    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()
    var index = 0
    when {
      key == moduleKey -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect { event ->
              loadingAdvertiser.onEvent(event)
              if (event is DependencyCompletionEvent.Item) {
                val item = event.result
                resultSet.addElement(
                  item,
                  item.groupId + ":" + item.artifactId,
                  GradleTomlLibraryCompletionPosition.MODULE,
                  parameters.isAutoPopup,
                  index++,
                )
              }
            }
        }
      }

      key == groupKey -> {
        val artifact = tomlLiteral.getSiblingValue(artifactKey)
        val request = DependencyGroupCompletionRequest(text, artifact, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestGroupCompletions(request)
            .collect { event ->
              loadingAdvertiser.onEvent(event)
              if (event is DependencyCompletionEvent.Item) {
                val item = event.result
                resultSet.addElement(item, item.result, GradleTomlLibraryCompletionPosition.GROUP, parameters.isAutoPopup, index++)
              }
            }
        }
      }

      key == artifactKey -> {
        val group = tomlLiteral.getSiblingValue(groupKey)
        val request = DependencyArtifactCompletionRequest(group, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestArtifactCompletions(request)
            .collect { event ->
              loadingAdvertiser.onEvent(event)
              if (event is DependencyCompletionEvent.Item) {
                val item = event.result
                resultSet.addElement(item, item.result, GradleTomlLibraryCompletionPosition.ARTIFACT, parameters.isAutoPopup, index++)
              }
            }
        }
      }

      key == versionKey -> {
        val (group, artifact) = tomlLiteral.getGroupAndArtifactForVersion()
        val request = DependencyVersionCompletionRequest(group, artifact, text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestVersionCompletions(request)
            .collect { event ->
              loadingAdvertiser.onEvent(event)
              if (event is DependencyCompletionEvent.Item) {
                val item = event.result
                resultSet.addElement(item, item.result, GradleTomlLibraryCompletionPosition.VERSION, parameters.isAutoPopup, index++)
              }
            }
        }
      }

      // must be at the end of when
      tomlKey.isDirectlyInLibrariesTable() -> {
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())
        runBlockingCancellable {
          completionService.suggestCompletions(request)
            .collect { event ->
              loadingAdvertiser.onEvent(event)
              if (event is DependencyCompletionEvent.Item) {
                val item = event.result
                resultSet.addElement(
                  item,
                  item.groupId + ":" + item.artifactId + ":" + item.version,
                  GradleTomlLibraryCompletionPosition.GAV,
                  parameters.isAutoPopup,
                  index++,
                )
              }
            }
        }
      }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }

  private fun CompletionResultSet.addElement(
    lookupObject: BaseDependencyCompletionResult,
    lookupString: String,
    pos: GradleTomlLibraryCompletionPosition,
    isAutoPopup: Boolean,
    index: Int,
  ) {
    val lookupElement = LookupElementBuilder
      .create(lookupString)
      .withPresentableText(lookupString)
      .withIcon(lookupObject.icon)
      .withInsertHandler(TomlStringInsertHandler)
    lookupElement.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)
    lookupElement.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(lookupObject.source, index))
    lookupElement.putUserData(BT_COMPLETION_IS_AUTO_POPUP, isAutoPopup)
    lookupElement.putUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY, pos)
    lookupElement.putUserData(SUPPRESS_QUICK_DEFINITION, true)
    lookupElement.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)

    this.addElement(MLRankingIgnorable.wrap(lookupElement))
  }

  private fun TomlLiteral.getGroupAndArtifactForVersion(): Pair<String, String> {
    val module = getSiblingValue(moduleKey)
    if (module.isNotEmpty()) return module.substringBefore(':') to module.substringAfter(':')

    val group = getSiblingValue(groupKey)
    val artifact = getSiblingValue(artifactKey)
    return group to artifact
  }
}

private object TomlStringInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val value = item.getObject() as? String ?: return
    context.commitDocument()
    val psiFile = PsiDocumentManager.getInstance(context.project).getPsiFile(context.document) ?: return
    val element = psiFile.findElementAt(context.startOffset) ?: return
    val text = element.text
    val range = element.textRange
    val contentStart = range.startOffset + if (text.startsWith('"') || text.startsWith('\'')) 1 else 0
    val contentEnd = range.endOffset - if (text.endsWith('"') || text.endsWith('\'')) 1 else 0
    context.document.replaceString(contentStart, contentEnd, value)
  }
}