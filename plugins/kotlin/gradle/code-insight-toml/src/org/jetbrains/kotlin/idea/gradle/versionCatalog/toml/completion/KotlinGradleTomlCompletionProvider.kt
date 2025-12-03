// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.completion.api.DependencyCompletionRequest
import org.jetbrains.idea.completion.api.DependencyCompletionService
import org.jetbrains.idea.completion.api.GradleDependencyCompletionContext
import org.jetbrains.kotlin.gradle.scripting.shared.completion.FullStringInsertHandler
import org.jetbrains.kotlin.gradle.scripting.shared.completion.KotlinGradleDependencyCompletionMatcher
import org.jetbrains.kotlin.gradle.scripting.shared.completion.removeDummySuffix
import org.jetbrains.kotlin.gradle.scripting.shared.completion.useDependencyCompletionService

private const val moduleKey = "module"

internal class KotlinGradleTomlCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        if (!useDependencyCompletionService()) {
            return
        }

        val positionElement = parameters.position

        if (!positionElement.isTomlValue()) {
            return
        }

        val dummyText = removeWrappingQuotes(positionElement.text)
        val text = removeDummySuffix(dummyText)
        val tomlKey = positionElement.getTomlKey()

        val completionService = service<DependencyCompletionService>()
        val request = DependencyCompletionRequest(text, GradleDependencyCompletionContext)

        val resultSet = result.withPrefixMatcher(KotlinGradleDependencyCompletionMatcher(text))

        when {
            tomlKey.endsWith(moduleKey) -> {
                runBlockingCancellable {
                    completionService.suggestCompletions(request)
                        .collect { item ->
                            val lookupString = item.groupId + ":" + item.artifactId
                            val lookupObject = "\"$lookupString\""
                            val lookupElement = LookupElementBuilder
                                .create(lookupObject, lookupString)
                                .withPresentableText(lookupString)
                                .withInsertHandler(FullStringInsertHandler)

                            resultSet.addElement(lookupElement)
                        }
                }
            }

            //TODO: complete group, name, version
        }

    }

    private fun removeWrappingQuotes(s: String): String {
        return if (s.length >= 2 &&
            ((s.startsWith('"') && s.endsWith('"')) ||
                    (s.startsWith('\'') && s.endsWith('\'')))) {
            s.substring(1, s.length - 1)
        } else s
    }

}