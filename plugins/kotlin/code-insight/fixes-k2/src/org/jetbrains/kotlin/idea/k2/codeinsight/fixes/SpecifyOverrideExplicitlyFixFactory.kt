// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMemberInfo
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal object SpecifyOverrideExplicitlyFixFactory {
    @OptIn(KaExperimentalApi::class)
    val specifyOverrideExplicitlyFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DelegatedMemberHidesSupertypeOverride ->
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

                    if (delegateTargetSymbol is KaValueParameterSymbol &&
                        delegateTargetSymbol.containingDeclaration.let {
                            it is KaConstructorSymbol &&
                                    it.isPrimary &&
                                    it.containingDeclaration == delegatedDeclaration.containingDeclaration
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

    context(_: KaSession)
    private fun KtDelegatedSuperTypeEntry.getSymbol(): KaNamedSymbol? {
        val nameReferenceExpression = delegateExpression as? KtNameReferenceExpression ?: return null
        return nameReferenceExpression.mainReference.resolveToSymbol() as? KaNamedSymbol
    }

    @KaExperimentalApi
    private val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        returnTypeFilter = KaCallableReturnTypeFilter.ALWAYS
        valueParameterRenderer = KaValueParameterSymbolRenderer.TYPE_ONLY
        keywordsRenderer = keywordsRenderer.with {
            keywordFilter = KaRendererKeywordFilter.without(
                KtTokens.FUN_KEYWORD
            )
        }
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter = KaRendererKeywordFilter.without(
                    KtTokens.OVERRIDE_KEYWORD,
                )
            }
        }
    }

    private data class ElementContext(
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

        override fun getPresentation(
            context: ActionContext,
            element: KtClassOrObject,
        ): Presentation {
            val (signature) = getElementContext(context, element)
            return Presentation.of(KotlinBundle.message("specify.override.for.0.explicitly", signature))
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("specify.override.explicitly")
    }
}
