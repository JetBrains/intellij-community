// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMemberInfo
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal object SpecifyOverrideExplicitlyFixFactory {
    val specifyOverrideExplicitlyFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.DelegatedMemberHidesSupertypeOverride ->
            val ktClass = diagnostic.psi
            if (ktClass.superTypeListEntries.any {
                    it is KtDelegatedSuperTypeEntry && it.delegateExpression !is KtNameReferenceExpression
                }) {
                return@ModCommandBased emptyList()
            }

            val delegatedDeclaration = diagnostic.delegatedDeclaration
            val delegateParameters = mutableListOf<SmartPsiElementPointer<KtParameter>>()
            val generatedMembers = mutableListOf<KtCallableDeclaration>()

            for (specifier in ktClass.superTypeListEntries) {
                if (specifier is KtDelegatedSuperTypeEntry) {
                    val delegateTargetSymbol = specifier.getSymbol() ?: return@ModCommandBased emptyList()

                    if (delegateTargetSymbol is KtValueParameterSymbol &&
                        delegateTargetSymbol.getContainingSymbol().let {
                            it is KtConstructorSymbol &&
                                    it.isPrimary &&
                                    it.getContainingSymbol() == delegatedDeclaration.getContainingSymbol()
                        }
                    ) {
                        val delegateParameter = delegateTargetSymbol.psi as? KtParameter
                        if (delegateParameter != null && !delegateParameter.hasValOrVar()) {
                            delegateParameters.add(delegateParameter.createSmartPointer())
                        }
                    }

                    val memberInfo = KtClassMemberInfo.create(
                        symbol = diagnostic.overriddenDeclaration,
                    )

                    val ktClassMember = KtClassMember(
                        memberInfo = memberInfo,
                        bodyType = BodyType.Delegate(delegateTargetSymbol.name.asString()),
                        preferConstructorParameter = false,
                    )

                    generateMember(
                        project = ktClass.project,
                        ktClassMember = ktClassMember,
                        symbol = delegatedDeclaration,
                        targetClass = ktClass,
                        copyDoc = false,
                    ).let(
                        generatedMembers::add
                    )
                }
            }

            val signature = delegatedDeclaration.render(renderer)
            val elementContext = ElementContext(signature, delegateParameters, generatedMembers)
            listOf(SpecifyOverrideExplicitlyFix(ktClass, elementContext))
        }

    context(KtAnalysisSession)
    private fun KtDelegatedSuperTypeEntry.getSymbol(): KtNamedSymbol? {
        val nameReferenceExpression = delegateExpression as? KtNameReferenceExpression ?: return null
        val declaration = nameReferenceExpression.reference?.resolve() as? KtDeclaration ?: return null
        return declaration.getSymbol() as? KtNamedSymbol
    }

    private val renderer = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        returnTypeFilter = KtCallableReturnTypeFilter.ALWAYS
        valueParameterRenderer = KtValueParameterSymbolRenderer.TYPE_ONLY
        keywordsRenderer = keywordsRenderer.with {
            keywordFilter = KtRendererKeywordFilter.without(
                KtTokens.FUN_KEYWORD
            )
        }
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter = KtRendererKeywordFilter.without(
                    KtTokens.OVERRIDE_KEYWORD,
                )
            }
        }
    }

    class ElementContext(
        val signature: String,
        val delegateParameters: MutableList<SmartPsiElementPointer<KtParameter>>,
        val generatedMembers: List<KtCallableDeclaration>,
    )

    private class SpecifyOverrideExplicitlyFix(
        element: KtClassOrObject,
        context: ElementContext
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtClassOrObject, ElementContext>(element, context) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtClassOrObject,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(element.project)
            val writableParams = elementContext.delegateParameters.mapNotNull { it.element }.map(updater::getWritable)

            for (param in writableParams) {
                param.addModifier(KtTokens.PRIVATE_KEYWORD)
                param.addAfter(factory.createValKeyword(), param.modifierList)
            }

            for (member in elementContext.generatedMembers) {
                element.addDeclaration(member).let(::shortenReferences)
            }
        }

        override fun getActionName(actionContext: ActionContext, element: KtClassOrObject, elementContext: ElementContext): String {
            return KotlinBundle.message("specify.override.for.0.explicitly", elementContext.signature)
        }

        override fun getFamilyName(): String {
            return KotlinBundle.message("specify.override.explicitly")
        }
    }
}
