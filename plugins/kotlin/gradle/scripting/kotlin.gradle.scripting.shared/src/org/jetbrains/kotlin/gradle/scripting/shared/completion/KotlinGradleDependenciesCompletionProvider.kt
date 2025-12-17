// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.idea.completion.api.*
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

private val exclude = setOf(
    "exclude",
)

internal class KotlinGradleDependenciesCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (!useDependencyCompletionService()) {
            return
        }

        val positionElement = parameters.position
        when {
            // server-side completion only
            // dependencies { juni<caret> }
            //positionElement.isOnTheTopLevelOfScriptBlock(DEPENDENCIES) ->
            //    suggestDependencyCompletions(result, parameters, DependencyConfigurationInsertHandler, TopLevelLookupStringProvider)

            // dependencies { implementation(...) { exclude("<caret>") } }
            positionElement.isDependencyArgument(exclude) ->
                suggestCoordinateCompletions(
                    result,
                    parameters,
                    positionElement.getGroupPrefix(),
                    positionElement.getExcludeArtifactPrefix(),
                    ""
                )

            // dependencies { implementation("juni<caret>", "juni", "") }
            positionElement.isPositionalOrNamedDependencyArgument() ->
                suggestCoordinateCompletions(
                    result,
                    parameters,
                    positionElement.getGroupPrefix(),
                    positionElement.getArtifactPrefix(),
                    positionElement.getVersionPrefix()
                )

            // dependencies { implementation("juni<caret>") }
            positionElement.isSingleDependencyArgument() ->
                suggestDependencyCompletions(result, parameters, FullStringInsertHandler, SimpleLookupStringProvider)
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
        lookupStringProvider: (DependencyCompletionResult) -> String
    ) {
        filterResultsFromOtherContributors(result, parameters)

        val documentText = parameters.editor.document.text
        val offset = parameters.offset
        val startOffset = getDependencyCompletionStartOffset(documentText, offset)
        val text = documentText.substring(startOffset, offset)

        val completionService = service<DependencyCompletionService>()
        val request = DependencyCompletionRequest(text, parameters.getCompletionContext())

        val resultSet = result.withPrefixMatcher(KotlinGradleDependencyCompletionMatcher(text))

        runBlockingCancellable {
            completionService.suggestCompletions(request)
                .collect { item ->
                    val lookupString = lookupStringProvider(item)
                    val lookupElement = LookupElementBuilder
                        .create(lookupString, lookupString)
                        .withPresentableText(lookupString)
                        .withInsertHandler(insertHandler)
                    lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())

                    resultSet.addElement(lookupElement)
                }
        }
    }


    private fun suggestCoordinateCompletions(
        result: CompletionResultSet,
        parameters: CompletionParameters,
        group: String,
        artifact: String,
        version: String
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
                val lookupElement = LookupElementBuilder.create(item, item)
                    .withPresentableText(item)
                    .withInsertHandler(FullStringInsertHandler)
                lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
                result.addElement(lookupElement)
            }
        }
    }

    private fun String.isBeingCompleted(): Boolean = this.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
}

/*private object TopLevelLookupStringProvider : (DependencyCompletionResult) -> String {
    override fun invoke(it: DependencyCompletionResult): String {
        val scope = it.scope
        if (!scope.isNullOrEmpty() && scope.contains(":")) {
            val items = scope.split(":")
            val configurationName = items[0]
            val dependencyNotation = items[1]
            if (configurationName in configurationNames && dependencyNotation in dependencyNotations)
                return "$configurationName($dependencyNotation(\"${it.groupId}:${it.artifactId}:${it.version}\"))"
        }
        val configurationName = if (scope in configurationNames) scope else defaultConfigurationName
        return "$configurationName(\"${it.groupId}:${it.artifactId}:${it.version}\")"
    }
}*/

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

