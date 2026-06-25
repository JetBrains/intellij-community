// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.codeInsight

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtScript

/**
 * Suggests public top-level declarations of scripts pulled in via the script configuration's imported-scripts
 * key (e.g. `@file:Import(...)` for `main.kts`), so they appear in ordinary (autopopup) completion.
 *
 * Imported-script declarations are compiled as members of the imported script's class. The platform's callable
 * completion only surfaces them through its broad index path, which runs on repeated explicit invocation
 * (`invocationCount > 1`); in autopopup and on the first explicit invocation they are missing. The scope-based
 * path cannot help because the completion file's analysis context exposes an empty package-member scope for
 * scripts. This provider fills that gap by reading the already-resolved imported scripts from the
 * module structure (as friend dependencies) and offering their declarations directly.
 */
internal class KotlinImportedScriptCompletionProvider : CompletionProvider<CompletionParameters>() {
    @OptIn(KaExperimentalApi::class)
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (parameters.invocationCount > 1) return

        val file = parameters.originalFile as? KtFile ?: return
        if (!file.name.endsWith(".kts") && !file.isScript()) return

        val prefixMatcher = result.prefixMatcher
        val completionElement = parameters.position as? KtElement ?: file
        analyze(completionElement) {
            val module = useSiteModule as? KaScriptModule ?: return@analyze
            val importedScriptModules = module.directFriendDependencies.filterIsInstance<KaScriptModule>()
            val visibilityChecker = createUseSiteVisibilityChecker(file.symbol, receiverExpression = null, position = completionElement)

            for (importedScriptModule in importedScriptModules) {
                val importedScript = importedScriptModule.file
                if (importedScript == file) continue
                processDeclarations(importedScript.declarations, importedScript.name, prefixMatcher, result, visibilityChecker)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.processDeclarations(
        declarations: List<KtDeclaration>,
        sourceFileName: String,
        prefixMatcher: com.intellij.codeInsight.completion.PrefixMatcher,
        result: CompletionResultSet,
        visibilityChecker: KaUseSiteVisibilityChecker,
    ) {
        for (declaration in declarations) {
            ProgressManager.checkCanceled()
            if (declaration is KtScript) {
                processDeclarations(declaration.declarations, sourceFileName, prefixMatcher, result, visibilityChecker)
            } else if (declaration is KtNamedDeclaration) {
                val symbol = declaration.symbol
                val name = (symbol as? KaCallableSymbol)?.name?.asString()
                    ?: (symbol as? KaClassLikeSymbol)?.name?.asString()
                    ?: continue
                if (prefixMatcher.prefixMatches(name) && visibilityChecker.isVisible(symbol)) {
                    result.addElement(createLookupElement(symbol, name, sourceFileName))
                }
            }
        }
    }

    private fun createLookupElement(symbol: KaDeclarationSymbol, name: String, sourceFileName: String): LookupElement {
        val builder = symbol.psi?.let { LookupElementBuilder.create(it, name) }
            ?: LookupElementBuilder.create(name)

        val builderWithSource = builder.withTypeText(sourceFileName, true)
        if (symbol !is KaNamedFunctionSymbol) return builderWithSource

        val hasParameters = symbol.valueParameters.isNotEmpty()
        return builderWithSource.withTailText("()", true).withInsertHandler { insertionContext, _ ->
            insertParentheses(insertionContext, hasParameters)
        }
    }

    private fun insertParentheses(context: InsertionContext, hasParameters: Boolean) {
        val offset = context.tailOffset
        if (context.document.charsSequence.getOrNull(offset) != '(') {
            context.document.insertString(offset, "()")
        }
        context.editor.caretModel.moveToOffset(offset + if (hasParameters) 1 else 2)
    }
}
