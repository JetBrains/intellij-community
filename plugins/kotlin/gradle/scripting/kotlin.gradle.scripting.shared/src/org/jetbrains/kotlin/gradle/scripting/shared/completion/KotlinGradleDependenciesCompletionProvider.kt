// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.idea.completion.api.*

/**
 * Contains dependency configurations, such as
 * `api`, `implementation`, `compileOnly` etc.:
 * *  [core](https://docs.gradle.org/current/userguide/dependency_configurations.html)
 * *  [java](https://docs.gradle.org/current/userguide/java_plugin.html)
 * *  [kotlin](https://kotlinlang.org/docs/kapt.html)
 */
private val configurationNames: List<String> by lazy {
    readLinesFromFile("/completion/dependencies-script-block.txt")
}

private val dependencyNotations = listOf(
    "platform",
    "enforcedPlatform",
)

private val exclude = setOf(
    "exclude",
)

private const val defaultConfigurationName = "implementation"

internal class KotlinGradleDependenciesCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (!useDependencyCompletionService()) {
            return
        }

        val positionElement = parameters.position
        when {
            // dependencies { juni<caret> }
            positionElement.isOnTheTopLevelOfScriptBlock(DEPENDENCIES) ->
                suggestDependencyCompletions(result, parameters, DependencyConfigurationInsertHandler, TopLevelLookupStringProvider)

            // dependencies { implementation("juni<caret>", "juni", "") }
            positionElement.isPositionalOrNamedDependencyArgument(configurationNames) ->
                suggestCoordinateCompletions(
                    result,
                    parameters,
                    positionElement.getGroupPrefix(),
                    positionElement.getArtifactPrefix(),
                    positionElement.getVersionPrefix()
                )

            // dependencies { implementation("juni<caret>") }
            positionElement.isSingleDependencyArgument(configurationNames + dependencyNotations) ->
                suggestDependencyCompletions(result, parameters, StringInsertHandler, SimpleLookupStringProvider)

            // dependencies { implementation(...) { exclude("<caret>") } }
            positionElement.isDependencyArgument(exclude) ->
                suggestCoordinateCompletions(
                    result,
                    parameters,
                    positionElement.getGroupPrefix(),
                    positionElement.getExcludeArtifactPrefix(),
                    ""
                )
        }
    }

    private fun suggestDependencyCompletions(
        result: CompletionResultSet,
        parameters: CompletionParameters,
        insertHandler: InsertHandler<LookupElement>,
        lookupStringProvider: (DependencyCompletionResult) -> String
    ) {
        val documentText = parameters.editor.document.text
        val offset = parameters.offset
        val startOffset = getDependencyCompletionStartOffset(documentText, offset)
        val text = documentText.substring(startOffset, offset)
        if (text.isEmpty()) return

        val completionService = service<DependencyCompletionService>()
        val request = DependencyCompletionRequest(text, GradleDependencyCompletionContext)

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
        val dummyText = parameters.position.parent.text
        val text = removeDummySuffix(dummyText)
        val completionService = service<DependencyCompletionService>()
        val itemFlow = when {
            group.isBeingCompleted() -> completionService.suggestGroupCompletions(
                DependencyGroupCompletionRequest(
                    text,
                    artifact,
                    GradleDependencyCompletionContext
                )
            )

            artifact.isBeingCompleted() -> completionService.suggestArtifactCompletions(
                DependencyArtifactCompletionRequest(
                    group,
                    text,
                    GradleDependencyCompletionContext
                )
            )

            version.isBeingCompleted() -> completionService.suggestVersionCompletions(
                DependencyVersionCompletionRequest(
                    group,
                    artifact,
                    text,
                    GradleDependencyCompletionContext
                )
            )

            else -> flowOf()
        }

        runBlockingCancellable {
            itemFlow.collect { item ->
                val lookupElement = LookupElementBuilder.create(item, item)
                    .withPresentableText(item)
                    .withInsertHandler(StringInsertHandler)
                lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
                result.addElement(lookupElement)
            }
        }
    }

    private fun String.isBeingCompleted(): Boolean = this.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)

    private fun removeDummySuffix(value: String?): String {
        if (value == null) {
            return ""
        }
        val index = value.indexOf(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
        val result = if (index >= 0) {
            value.take(index)
        } else {
            value
        }
        return result.trim()
    }
}

private object TopLevelLookupStringProvider : (DependencyCompletionResult) -> String {
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

private object StringInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val result = item.getObject() as? String ?: return
        // IDEA was so kind to have replaced part of the initial string for us,
        // but we would like to replace the whole string
        context.commitDocument()
        val docManager = PsiDocumentManager.getInstance(context.project)
        val psiFile = docManager.getPsiFile(context.document)!!
        val element = psiFile.findElementAt(context.startOffset)!!

        context.document.replaceString(
            element.startOffset,
            element.endOffset,
            result
        )
    }
}

