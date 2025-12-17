// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.completion.api.*
import org.jetbrains.kotlin.gradle.scripting.shared.completion.*
import org.jetbrains.plugins.gradle.completion.FullStringInsertHandler
import org.jetbrains.plugins.gradle.completion.GradleDependencyCompletionMatcher
import org.jetbrains.plugins.gradle.completion.getCompletionContext
import org.jetbrains.plugins.gradle.completion.removeDummySuffix
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService
import org.toml.lang.psi.TomlLiteral

private const val moduleKey = "module"
private const val groupKey = "group"
private const val artifactKey = "name"
private const val versionKey = "version"

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