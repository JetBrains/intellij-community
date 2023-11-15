// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableSymbol
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.KtIconProvider.getBaseIcon
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.OverridesCompletionLookupElementDecorator
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.lookups.withAllowedResolve
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtOverrideMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal class OverrideKeywordHandler(
    private val basicContext: FirBasicCompletionContext
) : CompletionKeywordHandler<KtAnalysisSession>(KtTokens.OVERRIDE_KEYWORD) {

    context(KtAnalysisSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> = createOverrideMemberLookups(parameters, declaration = null, project) + lookup

    context(KtAnalysisSession)
    fun createOverrideMemberLookups(
        parameters: CompletionParameters,
        declaration: KtCallableDeclaration?,
        project: Project
    ): Collection<LookupElement> {
        val result = mutableListOf<LookupElement>()
        val position = parameters.position
        val isConstructorParameter = position.getNonStrictParentOfType<KtPrimaryConstructor>() != null
        val parent = position.getNonStrictParentOfType<KtClassOrObject>() ?: return result
        val classOrObject = getOriginalDeclarationOrSelf(parent, basicContext.originalKtFile)
        val members = collectMembers(classOrObject, isConstructorParameter)

        for (member in members) {
            val symbolPointer = member.memberInfo.symbolPointer
            val memberSymbol = symbolPointer.restoreSymbol()
            requireNotNull(memberSymbol) { "${symbolPointer::class} can't be restored" }

            if (declaration != null && !canCompleteDeclarationWithMember(declaration, memberSymbol)) continue
            result += createLookupElementToGenerateSingleOverrideMember(member, declaration, classOrObject, isConstructorParameter, project)
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

    context(KtAnalysisSession)
    private fun canCompleteDeclarationWithMember(
        declaration: KtCallableDeclaration,
        symbolToOverride: KtCallableSymbol
    ): Boolean = when (declaration) {
        is KtFunction -> symbolToOverride is KtFunctionSymbol
        is KtValVarKeywordOwner -> {
            if (symbolToOverride !is KtVariableSymbol) {
                false
            } else {
                // val cannot override var
                !(declaration.isVal && !symbolToOverride.isVal)
            }
        }

        else -> false
    }

    context(KtAnalysisSession)
    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun createLookupElementToGenerateSingleOverrideMember(
        member: KtClassMember,
        declaration: KtCallableDeclaration?,
        classOrObject: KtClassOrObject,
        isConstructorParameter: Boolean,
        project: Project
    ): OverridesCompletionLookupElementDecorator {
        val symbolPointer = member.memberInfo.symbolPointer
        val memberSymbol = symbolPointer.restoreSymbol()
        requireNotNull(memberSymbol) { "${symbolPointer::class} can't be restored" }
        check(memberSymbol is KtNamedSymbol)
        check(classOrObject !is KtEnumEntry)

        val text = getSymbolTextForLookupElement(memberSymbol)
        val baseIcon = getBaseIcon(memberSymbol)
        val isImplement = (memberSymbol as? KtSymbolWithModality)?.modality == Modality.ABSTRACT
        val additionalIcon = if (isImplement) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod
        val icon = RowIcon(baseIcon, additionalIcon)
        val isSuspendFunction = (memberSymbol as? KtFunctionSymbol)?.isSuspend == true

        val containingSymbol = memberSymbol.unwrapFakeOverrides.originalContainingClassForOverride
        val baseClassName = containingSymbol?.name?.asString()
        val baseClassIcon = member.memberInfo.containingSymbolIcon

        val baseLookupElement = basicContext.lookupElementFactory.createLookupElement(memberSymbol, basicContext.importStrategyDetector)

        val classOrObjectPointer = classOrObject.createSmartPointer()
        return OverridesCompletionLookupElementDecorator(
            baseLookupElement,
            declaration,
            text,
            isImplement,
            icon,
            baseClassName,
            baseClassIcon,
            isConstructorParameter,
            isSuspendFunction,
            generateMember = {
                generateMemberInNewAnalysisSession(classOrObjectPointer.element!!, member, project)
            },
            shortenReferences = { element ->
                shortenReferencesInRange(element.containingKtFile, element.textRange)
            }
        )
    }

    context(KtAnalysisSession)
    private fun getSymbolTextForLookupElement(memberSymbol: KtCallableSymbol): String = buildString {
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
    ) = withAllowedResolve {
        analyze(classOrObject) {
            val symbolPointer = member.memberInfo.symbolPointer
            val symbol = symbolPointer.restoreSymbol()
            requireNotNull(symbol) { "${symbolPointer::class} can't be restored" }
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
                annotationRenderer = annotationRenderer.with {
                    annotationFilter = KtRendererAnnotationsFilter.NONE
                }
                modifiersRenderer = modifiersRenderer.with {
                    keywordsRenderer = keywordsRenderer.with { keywordFilter = KtRendererKeywordFilter.onlyWith(KtTokens.TYPE_MODIFIER_KEYWORDS) }
                }
            }
    }
}

private val KtValVarKeywordOwner.isVal: Boolean
    get() {
        val elementType = valOrVarKeyword?.node?.elementType
        return elementType == KtTokens.VAL_KEYWORD
    }
