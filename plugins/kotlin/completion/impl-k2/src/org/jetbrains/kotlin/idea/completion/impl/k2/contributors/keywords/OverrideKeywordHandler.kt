// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.KtIconProvider.getBaseIcon
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.OverridesCompletionLookupElementDecorator
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.withAllowedResolve
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal class OverrideKeywordHandler(
    private val basicContext: FirBasicCompletionContext
) : CompletionKeywordHandler<KaSession>(KtTokens.OVERRIDE_KEYWORD) {

    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> = createOverrideMemberLookups(parameters, declaration = null, project) + lookup

    context(KaSession)
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

    context(KaSession)
    private fun canCompleteDeclarationWithMember(
        declaration: KtCallableDeclaration,
        symbolToOverride: KaCallableSymbol
    ): Boolean = when (declaration) {
        is KtFunction -> symbolToOverride is KaNamedFunctionSymbol
        is KtValVarKeywordOwner -> {
            if (symbolToOverride !is KaVariableSymbol) {
                false
            } else {
                // val cannot override var
                !(declaration.isVal && !symbolToOverride.isVal)
            }
        }

        else -> false
    }

    context(KaSession)
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
        check(memberSymbol is KaNamedSymbol)
        check(classOrObject !is KtEnumEntry)

        val text = getSymbolTextForLookupElement(memberSymbol)
        val baseIcon = getBaseIcon(memberSymbol)
        val isImplement = memberSymbol.modality == KaSymbolModality.ABSTRACT
        val additionalIcon = if (isImplement) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod
        val icon = RowIcon(baseIcon, additionalIcon)
        val isSuspendFunction = (memberSymbol as? KaNamedFunctionSymbol)?.isSuspend == true

        val containingSymbol = memberSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol
        val baseClassName = containingSymbol?.name?.asString()
        val baseClassIcon = member.memberInfo.containingSymbolIcon

        val baseLookupElement = KotlinFirLookupElementFactory.createLookupElement(
            symbol = memberSymbol,
            importStrategyDetector = basicContext.importStrategyDetector,
        )

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

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getSymbolTextForLookupElement(memberSymbol: KaCallableSymbol): String = buildString {
        append(KtTokens.OVERRIDE_KEYWORD.value)
            .append(" ")
            .append(memberSymbol.render(renderingOptionsForLookupElementRendering))
        if (memberSymbol is KaNamedFunctionSymbol) {
            append(" {...}")
        }
    }

    @OptIn(KaExperimentalApi::class)
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
        @KaExperimentalApi
        private val renderingOptionsForLookupElementRendering =
            KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
                annotationRenderer = annotationRenderer.with {
                    annotationFilter = KaRendererAnnotationsFilter.NONE
                }
                modifiersRenderer = modifiersRenderer.with {
                    keywordsRenderer = keywordsRenderer.with { keywordFilter = KaRendererKeywordFilter.onlyWith(KtTokens.TYPE_MODIFIER_KEYWORDS) }
                }
            }
    }
}

private val KtValVarKeywordOwner.isVal: Boolean
    get() {
        val elementType = valOrVarKeyword?.node?.elementType
        return elementType == KtTokens.VAL_KEYWORD
    }
