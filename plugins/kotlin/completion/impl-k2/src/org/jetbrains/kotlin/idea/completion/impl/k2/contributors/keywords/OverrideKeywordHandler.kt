// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.completion.*
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtOverrideMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererModifierFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.idea.KtIconProvider.getIcon
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous

internal class OverrideKeywordHandler(
    private val basicContext: FirBasicCompletionContext
) : CompletionKeywordHandler<KtAnalysisSession>(KtTokens.OVERRIDE_KEYWORD) {

    override fun KtAnalysisSession.createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        val result = mutableListOf(lookup)
        val position = parameters.position
        val isConstructorParameter = position.getNonStrictParentOfType<KtPrimaryConstructor>() != null
        val classOrObject = position.getNonStrictParentOfType<KtClassOrObject>() ?: return result
        val members = collectMembers(classOrObject, isConstructorParameter)

        for (member in members) {
            result += createLookupElementToGenerateSingleOverrideMember(member, classOrObject, isConstructorParameter, project)
        }
        return result
    }

    private fun collectMembers(classOrObject: KtClassOrObject, isConstructorParameter: Boolean): List<KtClassMember> {
        val allMembers = KtOverrideMembersHandler().collectMembersToGenerate(classOrObject)
        return if (isConstructorParameter) {
            allMembers.mapNotNull { member ->
                if (member.memberInfo.isProperty) {
                    member.copy(bodyType = BodyType.FromTemplate, preferConstructorParameter = true)
                } else null
            }
        } else allMembers.toList()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun KtAnalysisSession.createLookupElementToGenerateSingleOverrideMember(
        member: KtClassMember,
        classOrObject: KtClassOrObject,
        isConstructorParameter: Boolean,
        project: Project
    ): OverridesCompletionLookupElementDecorator {
        val symbolPointer = member.memberInfo.symbolPointer
        val memberSymbol = symbolPointer.restoreSymbol()
        requireNotNull(memberSymbol) { "${symbolPointer::class} can't be restored"}
        check(memberSymbol is KtNamedSymbol)
        check(classOrObject !is KtEnumEntry)

        val text = getSymbolTextForLookupElement(memberSymbol)
        val baseIcon = getIcon(memberSymbol)
        val isImplement = (memberSymbol as? KtSymbolWithModality)?.modality == Modality.ABSTRACT
        val additionalIcon = if (isImplement) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod
        val icon = RowIcon(baseIcon, additionalIcon)
        val baseClass = classOrObject.getClassOrObjectSymbol()!!
        val baseClassIcon = getIcon(baseClass)
        val isSuspendFunction = (memberSymbol as? KtFunctionSymbol)?.isSuspend == true
        val baseClassName = baseClass.nameOrAnonymous.asString()

        val baseLookupElement = with(basicContext.lookupElementFactory) {
            createLookupElement(memberSymbol, basicContext.importStrategyDetector)
        }

        return OverridesCompletionLookupElementDecorator(
            baseLookupElement,
            declaration = null,
            text,
            isImplement,
            icon,
            baseClassName,
            baseClassIcon,
            isConstructorParameter,
            isSuspendFunction,
            generateMember = {
                generateMemberInNewAnalysisSession(classOrObject, member, project)
            },
            shortenReferences = { element ->
                val shortenings = allowAnalysisOnEdt {
                    analyze(classOrObject) {
                        collectPossibleReferenceShortenings(element.containingKtFile, element.textRange)
                    }
                }
                runWriteAction {
                    shortenings.invokeShortening()
                }
            }
        )
    }

    private fun KtAnalysisSession.getSymbolTextForLookupElement(memberSymbol: KtCallableSymbol): String = buildString {
        append(KtTokens.OVERRIDE_KEYWORD.value)
            .append(" ")
            .append(memberSymbol.render(renderingOptionsForLookupElementRendering))
        if (memberSymbol is KtFunctionSymbol) {
            append(" {...}")
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun generateMemberInNewAnalysisSession(
        classOrObject: KtClassOrObject,
        member: KtClassMember,
        project: Project
    ) = allowAnalysisOnEdt {
        analyze(classOrObject) {
            val symbolPointer = member.memberInfo.symbolPointer
            val symbol = symbolPointer.restoreSymbol()
            requireNotNull(symbol) { "${symbolPointer::class} can't be restored"}
            generateMember(
                project,
                member,
                symbol,
                classOrObject,
                copyDoc = false,
                mode = MemberGenerateMode.OVERRIDE,
            )
        }
    }

    companion object {
        private val renderingOptionsForLookupElementRendering =
            KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
                modifiersRenderer = modifiersRenderer.with {
                    modifierFilter = KtRendererModifierFilter.without(KtTokens.OPERATOR_KEYWORD) and
                            KtRendererModifierFilter.without(KtTokens.MODALITY_MODIFIERS)
                }
            }
    }
}