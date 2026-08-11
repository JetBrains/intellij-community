// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal class MainKtsDependsOnCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        result.runRemainingContributors(parameters) { _ -> result.stopHere() }

        val documentText = parameters.editor.document.text
        val offset = parameters.offset
        val start = findCoordinateStart(documentText, offset)
        val text = documentText.substring(start, offset)

        val completionService = service<DependencyCompletionService>()
        val completionContext = DependencyCompletionContextImpl(
            parameters.originalFile.project,
            ProjectSystemId("GRADLE"),
        )
        val request = DependencyCompletionRequest(text, completionContext)
        val resultSet = result.withPrefixMatcher(MavenCoordinatePrefixMatcher(text))

        runBlockingCancellable {
            completionService.suggestCompletions(request).collect { event ->
                if (event !is DependencyCompletionEvent.Item) return@collect
                val item = event.result
                val lookupString = "${item.groupId}:${item.artifactId}:${item.version}"
                val lookupElement = LookupElementBuilder.create(lookupString, lookupString)
                    .withPresentableText(lookupString)
                    .withIcon(item.icon)
                    .withInsertHandler(ReplaceFullCoordinateInsertHandler)
                lookupElement.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
                resultSet.addElement(lookupElement)
            }
        }
    }
}

private val DependencyCompletionResult.icon: Icon
    get() = when (source) {
        DependencyCompletionContributionSource.LOCAL -> AllIcons.Build.CompletionLocalCache
        DependencyCompletionContributionSource.SERVER -> AllIcons.Build.CompletionCloud
    }

@ApiStatus.Internal
fun findCoordinateStart(text: String, offset: Int): Int {
    var start = offset - 1
    while (start > 0 && text[start].isMavenCoordinateChar()) start--
    return start + 1
}

private fun Char.isMavenCoordinateChar(): Boolean =
    isLetterOrDigit() || this == '.' || this == '-' || this == '_' || this == ':'

private class MavenCoordinatePrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean =
        prefix.split(":").all { name.contains(it, ignoreCase = true) }

    override fun cloneWithPrefix(prefix: String): PrefixMatcher = MavenCoordinatePrefixMatcher(prefix)
}

private object ReplaceFullCoordinateInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val result = item.getObject() as? String ?: return
        context.commitDocument()
        val psiFile = PsiDocumentManager.getInstance(context.project).getPsiFile(context.document) ?: return
        val element = PsiUtilCore.getElementAtOffset(psiFile, context.startOffset)
        context.document.replaceString(element.startOffset, element.endOffset, result)
    }
}
