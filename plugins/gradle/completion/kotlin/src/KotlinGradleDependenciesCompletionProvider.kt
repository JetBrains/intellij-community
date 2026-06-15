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
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.completion.DependencyCompletionLoadingAdvertiser
import com.intellij.gradle.completion.FullStringInsertHandler
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.gradle.completion.GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY
import com.intellij.gradle.completion.GradleDependencyCompletionFuzzyMatcher
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.ARTIFACT
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.EMBEDDED_KOTLIN_MODULE
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.EXCLUDE_GROUP
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.EXCLUDE_MODULE
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.GAV
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.GROUP
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.KOTLIN_MODULE
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.KOTLIN_VERSION
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.OTHER
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.TOP_LEVEL
import com.intellij.gradle.completion.GradleScriptDependencyCompletionPosition.VERSION
import com.intellij.gradle.completion.getCompletionContext
import com.intellij.gradle.completion.icon
import com.intellij.gradle.completion.kotlin.insertHandler.KotlinGradleConfigurationInsertHandler
import com.intellij.gradle.completion.lookup.DependencyReturningMethodLookupProvider
import com.intellij.gradle.completion.removeDummySuffix
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiElement
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import com.intellij.repository.search.completion.statistics.BT_COMPLETION_IS_AUTO_POPUP
import com.intellij.util.ProcessingContext
import icons.GradleIcons
import kotlinx.coroutines.flow.flowOf

internal class KotlinGradleDependenciesCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (!isGradleDependenciesCompletionEnabled(parameters)) return

    val positionElement = parameters.position
    when {

      positionElement.isOnTheTopLevelOfScriptBlock(DEPENDENCIES) -> {
        // dependencies { implementatio<caret> }
        suggestConfigurations(result, parameters)

        // server-side completion only
        if (!isFreeMode()) {
          // dependencies { juni<caret> }
          suggestDependencyCompletions(
            result,
            parameters,
            DependencyConfigurationInsertHandler,
            TopLevelLookupStringProvider,
            invokePosition = TOP_LEVEL
          )
        }
      }

      // dependencies { implementation(...) { exclude("<caret>") } }
      positionElement.isExcludeArgument() -> {
        suggestCoordinateCompletions(
          result,
          parameters,
          positionElement.getGroupPrefix(),
          positionElement.getExcludeArtifactPrefix(),
          "",
          positionElement.getExcludeInvokePosition()
        )
      }

      // dependencies { implementation(kotlin("std<caret>")) }
      positionElement.isKotlinShortcutModuleArgument() -> {
        val callType = positionElement.getKotlinShortcutCall() ?: return
        when (callType) {
          KotlinShortcutCall.KOTLIN -> suggestKotlinShortcutModuleCompletions(result, parameters)
          KotlinShortcutCall.EMBEDDED_KOTLIN -> suggestEmbeddedKotlinShortcutModuleCompletions(result, parameters)
        }
      }

      // dependencies { implementation(kotlin("stdlib", "1.9<caret>")) }
      positionElement.isKotlinShortcutVersionArgument() -> {
        if (!positionElement.kotlinShortcutModuleHasVersion()) {
          suggestKotlinShortcutVersionCompletions(result, parameters, positionElement.getKotlinShortcutModuleText())
        }
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

      // dependencies { implementation("juni<caret>") }, dependencies { implementation(platform("juni<caret>")) }
      positionElement.isSingleDependencyArgumentInsideQuotes() ->
        suggestDependencyCompletions(
          result,
          parameters,
          FullStringInsertHandler,
          SimpleLookupStringProvider,
          invokePosition = GAV
        )

      // implementation(pl<caret>) -> implementation(platform(<caret>))
      positionElement.isSingleDependencyArgumentWithoutQuotesAndDots() ->
        result.addAllElements(DependencyReturningMethodLookupProvider.getElements())
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

  private fun suggestConfigurations(result: CompletionResultSet, parameters: CompletionParameters) {
    val dependencyConfigurations = findConfigurationsForDependencies(parameters.originalFile) ?: return
    val file = FileDocumentManager.getInstance().getFile(parameters.editor.document)
    val lookup = dependencyConfigurations.map { configurationName ->
      LookupElementBuilder.create(configurationName)
        .withInsertHandler(KotlinGradleConfigurationInsertHandler(
          isConfigurationNamePsiResolvable(configurationName, parameters.position, file)
        ))
        .withIcon(GradleIcons.Gradle)
        .withTypeText("Gradle Configuration")
        .also {
          it.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
        }
    }
    result.addAllElements(lookup)
  }

  private fun suggestDependencyCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
    insertHandler: InsertHandler<LookupElement>,
    lookupStringProvider: (DependencyCompletionResult) -> String,
    invokePosition: GradleScriptDependencyCompletionPosition,
  ) {
    val documentText = parameters.editor.document.text
    val offset = parameters.offset
    val startOffset = getDependencyCompletionStartOffset(documentText, offset)
    val text = documentText.substring(startOffset, offset)

    // Autocomplete the dependency coordinate only after 3 or more characters are typed
    if (parameters.isAutoPopup && text.length < 3) return

    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

    val completionService = service<DependencyCompletionService>()
    val request = DependencyCompletionRequest(text, parameters.getCompletionContext())

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    val seenLookupStrings = HashSet<String>()
    runBlockingCancellable {
      completionService.suggestCompletions(request)
        .collect { event ->
          loadingAdvertiser.onEvent(event)
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          val lookupString = lookupStringProvider(item)
          if (!seenLookupStrings.add(lookupString)) return@collect
          val lookupElement = LookupElementBuilder
            .create(item, lookupString)
            .withPresentableText(lookupString)
            .withIcon(item.icon)
            .withInsertHandler(insertHandler)

          val decoratedLookupElement = lookupElement.decorateLookupItem(index++, parameters.isAutoPopup, item.source, invokePosition)
          resultSet.addElement(decoratedLookupElement)
        }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }


  private fun suggestCoordinateCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
    group: String,
    artifact: String,
    version: String,
    invokePosition: GradleScriptDependencyCompletionPosition,
  ) {
    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

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

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      itemFlow.collect { event ->
        loadingAdvertiser.onEvent(event)
        if (event !is DependencyCompletionEvent.Item) return@collect
        val item = event.result
        val lookupElement = LookupElementBuilder.create(item, item.result)
          .withPresentableText(item.result)
          .withIcon(item.icon)
          .withInsertHandler(FullStringInsertHandler)

        val decoratedLookupElement = lookupElement.decorateLookupItem(index++, parameters.isAutoPopup, item.source, invokePosition)
        resultSet.addElement(decoratedLookupElement)
      }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }

  private fun suggestKotlinShortcutModuleCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
  ) {
    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

    val dummyText = parameters.position.parent.text
    val text = removeDummySuffix(dummyText)
    val completionService = service<DependencyCompletionService>()
    val request = DependencyCompletionRequest(
      "$KOTLIN_SHORTCUT_GROUP:$KOTLIN_SHORTCUT_ARTIFACT_PREFIX$text",
      parameters.getCompletionContext()
    )

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      completionService.suggestCompletions(request)
        .collect { event ->
          loadingAdvertiser.onEvent(event)
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          if (item.groupId != KOTLIN_SHORTCUT_GROUP || !item.artifactId.startsWith(KOTLIN_SHORTCUT_ARTIFACT_PREFIX)) return@collect
          val lookupString = "${item.artifactId.removePrefix(KOTLIN_SHORTCUT_ARTIFACT_PREFIX)}:${item.version}"
          val lookupElement = LookupElementBuilder.create(item, lookupString)
            .withPresentableText(lookupString)
            .withIcon(item.icon)
            .withInsertHandler(FullStringInsertHandler)

          val decoratedLookupElement = lookupElement.decorateLookupItem(index++, parameters.isAutoPopup, item.source, KOTLIN_MODULE)
          resultSet.addElement(decoratedLookupElement)
        }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }

  private fun suggestKotlinShortcutVersionCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
    moduleName: String,
  ) {
    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

    val dummyText = parameters.position.parent.text
    val text = removeDummySuffix(dummyText)
    val completionService = service<DependencyCompletionService>()
    val request = DependencyVersionCompletionRequest(
      KOTLIN_SHORTCUT_GROUP,
      "$KOTLIN_SHORTCUT_ARTIFACT_PREFIX$moduleName",
      text,
      parameters.getCompletionContext()
    )

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      completionService.suggestVersionCompletions(request)
        .collect { event ->
          loadingAdvertiser.onEvent(event)
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          val lookupElement = LookupElementBuilder.create(item, item.result)
            .withPresentableText(item.result)
            .withIcon(item.icon)
            .withInsertHandler(FullStringInsertHandler)

          val decoratedLookupElement = lookupElement.decorateLookupItem(index++, parameters.isAutoPopup, item.source, KOTLIN_VERSION)
          resultSet.addElement(decoratedLookupElement)
        }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }

  private fun suggestEmbeddedKotlinShortcutModuleCompletions(
    result: CompletionResultSet,
    parameters: CompletionParameters,
  ) {
    val loadingAdvertiser = DependencyCompletionLoadingAdvertiser()
    loadingAdvertiser.showSearchingStatus()

    val dummyText = parameters.position.parent.text
    val text = removeDummySuffix(dummyText)
    val completionService = service<DependencyCompletionService>()
    val request = DependencyArtifactCompletionRequest(
      KOTLIN_SHORTCUT_GROUP,
      "$KOTLIN_SHORTCUT_ARTIFACT_PREFIX$text",
      parameters.getCompletionContext(),
    )

    val resultSet = result.withPrefixMatcher(GradleDependencyCompletionFuzzyMatcher(text))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      completionService.suggestArtifactCompletions(request)
        .collect { event ->
          loadingAdvertiser.onEvent(event)
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          if (!item.result.startsWith(KOTLIN_SHORTCUT_ARTIFACT_PREFIX)) return@collect
          val lookupString = item.result.removePrefix(KOTLIN_SHORTCUT_ARTIFACT_PREFIX)
          val lookupElement = LookupElementBuilder.create(item, lookupString)
            .withPresentableText(lookupString)
            .withIcon(item.icon)
            .withInsertHandler(FullStringInsertHandler)

          val decoratedLookupElement =
            lookupElement.decorateLookupItem(index++, parameters.isAutoPopup, item.source, EMBEDDED_KOTLIN_MODULE)
          resultSet.addElement(decoratedLookupElement)
        }
    }
    loadingAdvertiser.onComplete()
    loadingAdvertiser.addServerErrorPlaceholderIfNeeded(resultSet, parameters.isAutoPopup, hadResults = index > 0)
  }

  private fun String.isBeingCompleted(): Boolean = this.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)

  private fun isFreeMode(): Boolean {
    return isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
  }

  private fun LookupElementBuilder.decorateLookupItem(
    index: Int,
    isAutoPopup: Boolean,
    source: DependencyCompletionContributionSource,
    invokePosition: GradleScriptDependencyCompletionPosition,
  ): LookupElement {
    this.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
    this.putUserData(GRADLE_DEPENDENCY_COMPLETION, true)
    this.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(source, index))
    this.putUserData(SUPPRESS_QUICK_DEFINITION, true)
    this.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)

    // Store FUS metadata
    this.putUserData(BT_COMPLETION_IS_AUTO_POPUP, isAutoPopup)
    this.putUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY, invokePosition)

    return MLRankingIgnorable.wrap(this)
  }
}

private object TopLevelLookupStringProvider : (DependencyCompletionResult) -> String {
  override fun invoke(it: DependencyCompletionResult): String {
    val scope = it.scope
    if (!scope.isNullOrEmpty() && scope.contains(",")) {
      val items = scope.split(",")
      val configurationName = items[0]
      val dependencyNotation = items[1]
      return "$configurationName($dependencyNotation(\"${it.groupId}:${it.artifactId}:${it.version}\"))"
    }
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
