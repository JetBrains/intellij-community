// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory.IntentionBased
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CallableUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.TypeAliasUsageReplacementStrategy
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.buildCodeToInline
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

object DeprecationFixFactory {
    val deprecatedWarning = IntentionBased { diagnostics: KaFirDiagnostic.Deprecation ->
        val kaSymbol = diagnostics.reference as? KaDeclarationSymbol ?: return@IntentionBased emptyList()
        createDeprecation(kaSymbol, diagnostics.psi)
    }

    val deprecatedError = IntentionBased { diagnostics: KaFirDiagnostic.DeprecationError ->
        val kaSymbol = diagnostics.reference as? KaDeclarationSymbol ?: return@IntentionBased emptyList()
        createDeprecation(kaSymbol, diagnostics.psi)
    }

    val deprecatedAliasWarning = IntentionBased { diagnostics: KaFirDiagnostic.TypealiasExpansionDeprecation ->
        val kaSymbol = diagnostics.reference as? KaDeclarationSymbol ?: return@IntentionBased emptyList()
        createDeprecation(kaSymbol, diagnostics.psi)
    }

    val deprecatedAliasError = IntentionBased { diagnostics: KaFirDiagnostic.TypealiasExpansionDeprecationError ->
        val kaSymbol = diagnostics.reference as? KaDeclarationSymbol ?: return@IntentionBased emptyList()
        createDeprecation(kaSymbol, diagnostics.psi)
    }

    context(KaSession)
    private fun createDeprecation(
        kaSymbol: KaDeclarationSymbol,
        psi: PsiElement
    ): List<IntentionAction> {
        val referenceExpression = when (val psiElement = psi) {
            is KtArrayAccessExpression -> psiElement
            is KtSimpleNameExpression -> psiElement
            is KtTypeReference -> (psiElement.typeElement as? KtUserType)?.referenceExpression
            is KtConstructorCalleeExpression -> psiElement.constructorReferenceExpression
            is KtBinaryExpression -> psiElement.operationReference
            else -> null
        } ?: return emptyList()
        val expression = (referenceExpression.parent as? KtCallExpression)?.takeIf {
            (kaSymbol as? KaNamedFunctionSymbol)?.isOperator == true && referenceExpression.mainReference.resolve() is KtValVarKeywordOwner
        } ?: referenceExpression
        val replaceWithData =
            fetchReplaceWithPattern(kaSymbol) ?: return emptyList()

        return listOf(DeprecatedSymbolUsageFix(expression, replaceWithData))
    }
}

class DeprecatedSymbolUsageFix(
    element: KtReferenceExpression,
    replaceWith: ReplaceWithData
) : DeprecatedSymbolUsageFixBase(element, replaceWith), HighPriorityAction {
    override fun getFamilyName() = KotlinBundle.message("replace.deprecated.symbol.usage")
    override fun getText() = KotlinBundle.message("replace.with.0", replaceWith.pattern)

    override fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?) {
        val element = element ?: return
        val replacer = replacementStrategy.createReplacer( element) ?: return
        val result = replacer() ?: return

        if (editor != null) {
            val offset = (result.getCalleeExpressionIfAny() ?: result).textOffset
            editor.moveCaret(offset)
        }
    }
}

abstract class DeprecatedSymbolUsageFixBase(
    element: KtReferenceExpression,
    val replaceWith: ReplaceWithData
) : KotlinPsiOnlyQuickFixAction<KtReferenceExpression>(element) {

    internal val isAvailable: Boolean

    init {
        assert(!isDispatchThread()) {
            "${javaClass.name} should not be created on EDT"
        }
        isAvailable = buildUsageReplacementStrategy(
            element,
            replaceWith
        )?.let { it.createReplacer(element) != null } == true
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null && isAvailable

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val strategy = buildUsageReplacementStrategy(expression, replaceWith) ?: return
        invoke(strategy, project, editor)
    }

    protected abstract operator fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?)

    companion object {

        private fun buildUsageReplacementStrategy(
            element: KtReferenceExpression,
            replaceWith: ReplaceWithData,
        ): UsageReplacementStrategy? {

            val target = element.mainReference.resolve()
            when (target) {
                is KtCallableDeclaration -> {
                    val context = (if (target is KtFunction) (target.bodyBlockExpression ?: target.bodyExpression
                    ?: target.valueParameterList?.parameters?.lastOrNull()) else null)
                        ?: (target as? KtProperty)?.getter ?: (target as? KtProperty)?.setter ?: (target as? KtProperty)?.initializer
                        ?: target
                    val psiFactory = KtPsiFactory(element.project)
                    val expression =
                        psiFactory.createExpressionCodeFragment(replaceWith.pattern, context).getContentElement() ?: return null

                    val replacement = buildCodeToInline(
                        target, expression, false, null, CodeToInlineBuilder(
                            original = target
                        )
                    ) ?: return null
                    return CallableUsageReplacementStrategy(replacement, inlineSetter = false)
                }
                is KtTypeAlias -> {
                    return TypeAliasUsageReplacementStrategy(target)
                }
                else -> return null
            }
        }
    }
}

context(KaSession)
fun fetchReplaceWithPattern(
    symbol: KaDeclarationSymbol
): ReplaceWithData? {
    val annotation = symbol.annotations.find { it.classId?.asSingleFqName() == StandardNames.FqNames.deprecated } ?: return null
    val replaceWithValue =
      (annotation.arguments.find { it.name.asString() == "replaceWith" }?.expression as? KaAnnotationValue.NestedAnnotationValue)?.annotationValue
            ?: return null
    val pattern =
        ((replaceWithValue.arguments.find { it.name.asString() == "expression" }?.expression as? KaConstantAnnotationValue)?.value as? KaConstantValue.StringValue)?.value
            ?: return null
    val imports =
        (replaceWithValue.arguments.find { it.name.asString() == "expression" }?.expression as? KaArrayAnnotationValue)?.values?.mapNotNull { ((it as? KaConstantAnnotationValue)?.value as? KaConstantValue.StringValue)?.value }
            ?: emptyList()

    return ReplaceWithData(pattern, imports, true)
}