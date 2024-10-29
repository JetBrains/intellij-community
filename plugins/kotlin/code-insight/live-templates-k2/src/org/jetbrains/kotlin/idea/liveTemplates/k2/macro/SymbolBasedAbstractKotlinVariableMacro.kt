// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.codeInsight.ExpectedExpressionMatcherProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.liveTemplates.macro.KotlinMacro
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.renderer.render

abstract class SymbolBasedAbstractKotlinVariableMacro : KotlinMacro() {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        if (params.isNotEmpty()) {
            return null
        }

        return resolveCandidates(context) f@ { _, variables ->
            val variable = variables.firstOrNull() ?: return@f null
            TextResult(variable.name.render())
        }
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext): Array<LookupElement>? {
        if (params.isNotEmpty()) {
            return emptyArray()
        }

        return resolveCandidates(context) f@ { file, variables ->
            val importStrategyDetector = ImportStrategyDetector(file, context.project)
            variables.mapTo(ArrayList()) { KotlinFirLookupElementFactory.createLookupElement(it, importStrategyDetector) }
                .toTypedArray()
        }
    }

    protected abstract val filterByExpectedType: Boolean

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun <T : Any> resolveCandidates(
        context: ExpressionContext,
        mapper: context(KaSession) (KtFile, Sequence<KaVariableSymbol>) -> T?
    ): T? {
        val project = context.project
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitAllDocuments()

        val document = context.editor?.document ?: return null
        val file = psiDocumentManager.getPsiFile(document) as? KtFile ?: return null

        val targetElement = file.findElementAt(context.startOffset) ?: return null
        val contextElement = targetElement.getNonStrictParentOfType<KtElement>() ?: return null

        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(contextElement) {
                    val matcher = with (ExpectedExpressionMatcherProvider) {
                        if (filterByExpectedType) get(contextElement) else null
                    }

                    val scope = file.scopeContext(contextElement).compositeScope { it !is KaScopeKind.ImportingScope }
                    val variables = scope.callables
                      .filterIsInstance<KaVariableSymbol>()
                      .filter { !it.name.isSpecial && shouldDisplayVariable(it, file) }
                      .filter { matcher == null || matcher. match(it.returnType) }

                    return mapper(this@analyze, file, variables)
                }
            }
        }
    }

    context(KaSession)
    private fun shouldDisplayVariable(variable: KaVariableSymbol, file: KtFile): Boolean {
        return when (variable) {
            is KaValueParameterSymbol, is KaLocalVariableSymbol -> true
            is KaKotlinPropertySymbol -> variable.psi?.containingFile == file
            else -> false
        }
    }
}