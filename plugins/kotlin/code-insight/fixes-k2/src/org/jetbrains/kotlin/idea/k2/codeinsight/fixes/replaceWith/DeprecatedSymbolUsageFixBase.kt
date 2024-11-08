// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaConstructorSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaNamedFunctionSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KaNamedClassSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory.IntentionBased
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CallableUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.ClassUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.buildCodeToInline
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.psi.*
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

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createDeprecation(
        kaSymbol: KaDeclarationSymbol,
        psi: PsiElement
    ): List<IntentionAction> {
        val deprecatedSymbol = kaSymbol.takeIf { it.deprecationStatus != null }
            ?: (kaSymbol.containingSymbol as? KaDeclarationSymbol)?.takeIf { it.deprecationStatus != null }
            ?: return emptyList()
        val referenceExpression = when (val psiElement = psi) {
            is KtArrayAccessExpression -> psiElement
            is KtSimpleNameExpression -> psiElement
            is KtTypeReference -> {
                val typeElement = psiElement.typeElement
                (((typeElement as? KtNullableType)?.innerType ?: typeElement) as? KtUserType)?.referenceExpression
            }
            is KtConstructorCalleeExpression -> psiElement.constructorReferenceExpression
            is KtBinaryExpression -> psiElement.operationReference
            else -> null
        } ?: return emptyList()
        val expression = (referenceExpression.parent as? KtCallExpression)?.takeIf {
            (deprecatedSymbol as? KaNamedFunctionSymbol)?.isOperator == true && referenceExpression.mainReference.resolve() is KtValVarKeywordOwner
        } ?: referenceExpression

        val replaceWithData = fetchReplaceWithPattern(deprecatedSymbol) ?: return emptyList()

        val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            modifiersRenderer = modifiersRenderer.with {
                keywordsRenderer = keywordsRenderer.with { keywordFilter = KaRendererKeywordFilter.NONE }
            }
            namedClassRenderer = KaNamedClassSymbolRenderer.AS_SOURCE_WITHOUT_PRIMARY_CONSTRUCTOR
            parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.NO_DEFAULT_VALUE
            constructorRenderer = KaConstructorSymbolRenderer.AS_RAW_SIGNATURE
            namedFunctionRenderer = KaNamedFunctionSymbolRenderer.AS_RAW_SIGNATURE
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.NONE
            }
            keywordsRenderer = keywordsRenderer.with { keywordFilter = KaRendererKeywordFilter.NONE }
        }
        val text = KotlinBundle.message("replace.usages.of.0.in.whole.project", deprecatedSymbol.render(renderer))
        return listOf(
            DeprecatedSymbolUsageFix(expression, replaceWithData),
            DeprecatedSymbolUsageInWholeProjectFix(expression, replaceWithData, text)
        )
    }
}

class DeprecatedSymbolUsageFix(
    element: KtReferenceExpression,
    replaceWith: ReplaceWithData
) : DeprecatedSymbolUsageFixBase(element, replaceWith), HighPriorityAction {
    override fun getFamilyName(): String = KotlinBundle.message("replace.deprecated.symbol.usage")
    override fun getText(): String = KotlinBundle.message("replace.with.0", replaceWith.pattern)

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
            element, replaceWith
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

            val target = element.mainReference.resolve()?.let { it.navigationElement ?: it }
            when (target) {
                is KtPrimaryConstructor, is KtClassLikeDeclaration -> {
                    val psiFactory = KtPsiFactory(element.project)
                    val typeReference = try {
                        psiFactory.createType(replaceWith.pattern)
                    } catch (e: Exception) {
                        if (e is ControlFlowException) throw e
                        val replacement = createReplacement(target as KtDeclaration, element, replaceWith) ?: return null

                        return CallableUsageReplacementStrategy(replacement, inlineSetter = false)
                    }

                    val typeElement = typeReference.typeElement as? KtUserType ?: return null

                    return ClassUsageReplacementStrategy(typeElement, null, element.project)
                }

                is KtCallableDeclaration -> {
                    val replacement = createReplacement(target, element, replaceWith) ?: return null

                    return CallableUsageReplacementStrategy(replacement, inlineSetter = false)
                }

                else -> return null
            }
        }

        private fun createReplacement(
            target: KtDeclaration,
            element: KtReferenceExpression,
            replaceWith: ReplaceWithData
        ): CodeToInline? {
            val context = (if (target is KtFunction) (target.bodyBlockExpression ?: target.bodyExpression
            ?: target.valueParameterList?.parameters?.lastOrNull()) else null)
                ?: (target as? KtProperty)?.getter ?: (target as? KtProperty)?.setter ?: (target as? KtProperty)?.initializer
                ?: target
            val psiFactory = KtPsiFactory(element.project)
            val expression =
                psiFactory.createExpressionCodeFragment(replaceWith.pattern, context).getContentElement() ?: return null

            return buildCodeToInline(target, expression, false, null, object : CodeToInlineBuilder(original = target) {
                override fun saveComments(codeToInline: MutableCodeToInline, contextDeclaration: KtDeclaration) {}
            })
        }
    }
}

fun fetchReplaceWithPattern(
    symbol: KaDeclarationSymbol
): ReplaceWithData? {
    val annotation = symbol.annotations.find { it.classId?.asSingleFqName() == StandardNames.FqNames.deprecated } ?: return null
    val replaceWithValue =
      (annotation.arguments.find { it.name.asString() == "replaceWith" }?.expression as? KaAnnotationValue.NestedAnnotationValue)?.annotation
            ?: return null
    val pattern =
        ((replaceWithValue.arguments.find { it.name.asString() == "expression" }?.expression as? KaAnnotationValue.ConstantValue)?.value as? KaConstantValue.StringValue)?.value
            ?: return null
    val imports =
        (replaceWithValue.arguments.find { it.name.asString() == "expression" }?.expression as? KaAnnotationValue.ArrayValue)?.values?.mapNotNull { ((it as? KaAnnotationValue.ConstantValue)?.value as? KaConstantValue.StringValue)?.value }
            ?: emptyList()

    return ReplaceWithData(pattern, imports, true)
}