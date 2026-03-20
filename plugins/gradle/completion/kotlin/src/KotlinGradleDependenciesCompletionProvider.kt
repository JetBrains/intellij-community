// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.completion.FullStringInsertHandler
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.gradle.completion.GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY
import com.intellij.gradle.completion.GradleDependencyCompletionFuzzyMatcher
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.ARTIFACT
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.EXCLUDE_MODULE
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.GAV
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.GROUP
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.OTHER
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.TOP_LEVEL
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.VERSION
import com.intellij.gradle.completion.getCompletionContext
import com.intellij.gradle.completion.removeDummySuffix
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.psi.PsiElement
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.statistics.BT_COMPLETION_IS_AUTO_POPUP
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

private val exclude = setOf(
  "exclude",
)

internal class KotlinGradleDependenciesCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (!useDependencyCompletionService()) {
      return
    }

    if (parameters.isAndroidProject()) {
      return
    }

    val positionElement = parameters.position
    when {
      // server-side completion only
      // dependencies { juni<caret> }
      !isFreeMode() && positionElement.isOnTheTopLevelOfScriptBlock(DEPENDENCIES) -> suggestDependencyCompletions(
        result,
        parameters,
        DependencyConfigurationInsertHandler,
        TopLevelLookupStringProvider,
        invokePosition = TOP_LEVEL
      )

      // dependencies { implementation(...) { exclude("<caret>") } }
      positionElement.isDependencyArgument(exclude) -> {
        suggestCoordinateCompletions(
          result,
          parameters,
          positionElement.getGroupPrefix(),
          positionElement.getExcludeArtifactPrefix(),
          "",
          positionElement.getExcludeInvokePosition()
        )
      }

      // dependencies { implementation("juni<caret>", "juni", "") }
      positionElement.isPositionalOrNamedDependencyArgument() -> {
        suggestCoordinateCompletions(
          result,
          parameters,
          positionElement.getGroupPrefix(),
          positionElement.getArtifactPrefix(),
          positionElement.getVersionPrefix(),
          positionElement.getPositionalOrNamedInvokePosition()
        )
      }

      // dependencies { implementation("juni<caret>") }
      positionElement.isSingleDependencyArgument() -> suggestDependencyCompletions(
        result,
        parameters,
        FullStringInsertHandler,
        SimpleLookupStringProvider,
        invokePosition = GAV
      )
    }
  }

  private fun PsiElement.getExcludeInvokePosition(): GradleScriptDependencyCompletionPosition {
    return when (argumentName) {
      "group" -> EXCLUDE_GROUP
      "module" -> EXCLUDE_MODULE
      else -> {
        when (argumentIndex) {
          0 -> EXCLUDE_GROUP
          1 -> EXCLUDE_MODULE
          else -> OTHER
        }
      }
    }
  }

  private fun PsiElement.getPositionalOrNamedInvokePosition(): GradleScriptDependencyCompletionPosition {
    return when (argumentName) {
      "group" -> GROUP
      "name" -> ARTIFACT
      "version" -> VERSION
      else -> {
        when (argumentIndex) {
          0 -> GROUP
          1 -> ARTIFACT
          2 -> VERSION
          else -> OTHER
        }
      }
    }
  }

  private fun filterResultsFromOtherContributors(result: CompletionResultSet, parameters: CompletionParameters) {
    result.runRemainingContributors(parameters) { _ ->
      // don't call other contributors
      result.stopHere()
    }
  }

  private fun suggestDependencyCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
    insertHandler: InsertHandler<LookupElement>,
    lookupStringProvider: (DependencyCompletionResult) -> String,
    invokePosition: GradleScriptDependencyCompletionPosition,
  ) {
    filterResultsFromOtherContributors(result, parameters)

    val documentText = parameters.editor.document.text
    val offset = parameters.offset
    val startOffset = getDependencyCompletionStartOffset(documentText, offset)
    val text = documentText.substring(startOffset, offset)

    val completionService = service<DependencyCompletionService>()
    val request = DependencyCompletionRequest(text, parameters.getCompletionContext())

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))

    var index = 0
    runBlockingCancellable {
      completionService.suggestCompletions(request)
        .collect { item ->
          val lookupString = lookupStringProvider(item)
          val lookupElement = LookupElementBuilder
            .create(item, lookupString)
            .withPresentableText(lookupString)
            .withInsertHandler(insertHandler)
          lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
          lookupElement.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)
          lookupElement.putUserData(StrictOrderWeigher.ORDER_KEY, index++)

          // Store FUS metadata
          lookupElement.putUserData(BT_COMPLETION_IS_AUTO_POPUP, parameters.isAutoPopup)
          lookupElement.putUserData(
            GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY,
            invokePosition
          )

          resultSet.addElement(lookupElement)
        }
    }
  }


  private fun suggestCoordinateCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
    group: String,
    artifact: String,
    version: String,
    invokePosition: GradleScriptDependencyCompletionPosition,
  ) {
    filterResultsFromOtherContributors(result, parameters)

    val dummyText = parameters.position.parent.text
    val text = removeDummySuffix(dummyText)
    val completionService = service<DependencyCompletionService>()
    val completionContext = parameters.getCompletionContext()
    val itemFlow = when {
      group.isBeingCompleted() -> completionService.suggestGroupCompletions(
        DependencyGroupCompletionRequest(
          text,
          artifact,
          completionContext
        )
      )

      artifact.isBeingCompleted() -> completionService.suggestArtifactCompletions(
        DependencyArtifactCompletionRequest(
          group,
          text,
          completionContext
        )
      )

      version.isBeingCompleted() -> completionService.suggestVersionCompletions(
        DependencyVersionCompletionRequest(
          group,
          artifact,
          text,
          completionContext
        )
      )

      else -> flowOf()
    }

    runBlockingCancellable {
      itemFlow.collect { item ->
        val lookupElement = LookupElementBuilder.create(item, item.result)
          .withPresentableText(item.result)
          .withInsertHandler(FullStringInsertHandler)
        lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
        lookupElement.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)

        // Store FUS metadata
        lookupElement.putUserData(BT_COMPLETION_IS_AUTO_POPUP, parameters.isAutoPopup)
        lookupElement.putUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY, invokePosition)

        result.addElement(lookupElement)
      }
    }
  }

  private fun String.isBeingCompleted(): Boolean = this.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)

  private fun CompletionParameters.isAndroidProject(): Boolean {
    val snapshot = this.originalFile.manager.project.workspaceModel.currentSnapshot
    return snapshot.entities<FacetEntity>().any { it.name == "Android" }
  }

  private fun isFreeMode(): Boolean {
    return isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
  }
}

private object TopLevelLookupStringProvider : (DependencyCompletionResult) -> String {
  override fun invoke(it: DependencyCompletionResult): String {
    val scope = it.scope
    /*  if (!scope.isNullOrEmpty() && scope.contains(":")) {
          val items = scope.split(":")
          val configurationName = items[0]
          val dependencyNotation = items[1]
          if (configurationName in configurationNames && dependencyNotation in dependencyNotations)
              return "$configurationName($dependencyNotation(\"${it.groupId}:${it.artifactId}:${it.version}\"))"
      }*/
    val configurationName = if (scope.isNullOrBlank()) "implementation" else scope
    return "$configurationName(\"${it.groupId}:${it.artifactId}:${it.version}\")"
  }
}

private object SimpleLookupStringProvider : (DependencyCompletionResult) -> String {
  override fun invoke(it: DependencyCompletionResult): String {
    return "${it.groupId}:${it.artifactId}:${it.version}"
  }
}

private object DependencyConfigurationInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    context.commitDocument()

    val text = context.document.text

    val endOffset = getDependencyCompletionEndOffset(text, context.tailOffset)
    context.document.deleteString(context.tailOffset, endOffset)

    val startOffset = getDependencyCompletionStartOffset(text, context.startOffset)
    context.document.deleteString(startOffset, context.startOffset)
  }
}

