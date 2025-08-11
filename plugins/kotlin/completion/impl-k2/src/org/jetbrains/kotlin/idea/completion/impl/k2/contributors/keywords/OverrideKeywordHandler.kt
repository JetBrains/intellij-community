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
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiversOwner
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.KaContextReceiversRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaFunctionLikeBodyRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.OverridesCompletionLookupElementDecorator
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.PreferAbstractForOverrideWeigher
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.renderVerbose
import org.jetbrains.kotlin.idea.completion.lookups.withAllowedResolve
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal class OverrideKeywordHandler(
    private val importStrategyDetector: ImportStrategyDetector,
) : CompletionKeywordHandler<KaSession>(KtTokens.OVERRIDE_KEYWORD) {

    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project,
    ): Collection<LookupElement> {
        val parameters = KotlinFirCompletionParameters.create(parameters)
            ?: return listOf(lookup)

        return createOverrideMemberLookups(
            parameters = parameters,
            declaration = null,
            project = project
        ) + lookup
    }

    context(KaSession)
    fun createOverrideMemberLookups(
        parameters: KotlinFirCompletionParameters,
        declaration: KtCallableDeclaration?,
        project: Project,
    ): Collection<LookupElement> {
        val result = mutableListOf<LookupElement>()
        val position = parameters.position
        val isConstructorParameter = position.getNonStrictParentOfType<KtPrimaryConstructor>() != null
        val parent = position.getNonStrictParentOfType<KtClassOrObject>() ?: return result
        val classOrObject = getOriginalDeclarationOrSelf(parent, parameters.originalFile)
        val members = collectMembers(classOrObject, isConstructorParameter)

        for (member in members) {
            val symbolPointer = member.memberInfo.symbolPointer
            val memberSymbol = symbolPointer.restoreSymbol()
            requireNotNull(memberSymbol) { "${symbolPointer::class} can't be restored" }

            if (declaration != null && !canCompleteDeclarationWithMember(declaration, memberSymbol)) continue

            val overrideLookupElement = createLookupElementToGenerateSingleOverrideMember(member, declaration, classOrObject, isConstructorParameter, project)
            PreferAbstractForOverrideWeigher.addWeight(overrideLookupElement)
            result += overrideLookupElement
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
    @OptIn(KaExperimentalApi::class)
    private fun createLookupElementToGenerateSingleOverrideMember(
        member: KtClassMember,
        declaration: KtCallableDeclaration?,
        classOrObject: KtClassOrObject,
        isConstructorParameter: Boolean,
        project: Project,
    ): OverridesCompletionLookupElementDecorator {
        val symbolPointer = member.memberInfo.symbolPointer
        val memberSymbol = symbolPointer.restoreSymbol()
        requireNotNull(memberSymbol) { "${symbolPointer::class} can't be restored" }
        check(memberSymbol is KaNamedSymbol)

        val baseIcon = KotlinIconProvider.getBaseIcon(memberSymbol)
        val isImplement = memberSymbol.modality == KaSymbolModality.ABSTRACT
        val additionalIcon = if (isImplement) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod
        val icon = RowIcon(baseIcon, additionalIcon)

        val containingSymbol = memberSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol

        val baseLookupElement = KotlinFirLookupElementFactory.createLookupElement(
            symbol = memberSymbol,
            importStrategyDetector = importStrategyDetector,
        )

        val classOrObjectPointer = classOrObject.createSmartPointer()
        return OverridesCompletionLookupElementDecorator(
            lookupElement = baseLookupElement,
            declaration = declaration,
            text = "override " + memberSymbol.render(TextRenderer),
            tailText = memberSymbol.contextReceivers
                .takeUnless { it.isEmpty() }
                ?.joinToString(prefix = " for ") { it.type.renderVerbose() }, // TODO could be a different DeclarationRenderer rendering only tail text information
            isImplement = isImplement,
            icon = icon,
            baseClassName = containingSymbol?.name?.asString(),
            baseClassIcon = member.memberInfo.containingSymbolIcon,
            isConstructorParameter = isConstructorParameter,
            isSuspend = (memberSymbol as? KaNamedFunctionSymbol)?.isSuspend == true,
            generateMember = {
                generateMemberInNewAnalysisSession(classOrObjectPointer.element!!, member, project)
            },
            shortenReferences = { element ->
                shortenReferencesInRange(element.containingKtFile, element.textRange)
            }
        )
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
}

@KaExperimentalApi
private val TextRenderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
    annotationRenderer = annotationRenderer.with {
        annotationFilter = KaRendererAnnotationsFilter.NONE
    }

    modifiersRenderer = modifiersRenderer.with {
        keywordsRenderer = keywordsRenderer.with {
            keywordFilter = KaRendererKeywordFilter.onlyWith(KtTokens.TYPE_MODIFIER_KEYWORDS)
        }
    }

    contextReceiversRenderer = contextReceiversRenderer.with {
        contextReceiverListRenderer = ContextParametersListRenderer
    }

    typeRenderer = typeRenderer.with {
        contextReceiversRenderer = KaContextReceiversRenderer {
            contextReceiverListRenderer = object : KaContextReceiverListRenderer {

                override fun renderContextReceivers(
                    analysisSession: KaSession,
                    owner: KaContextReceiversOwner,
                    contextReceiversRenderer: KaContextReceiversRenderer,
                    typeRenderer: KaTypeRenderer,
                    printer: PrettyPrinter,
                ) {
                }
            }
            contextReceiverLabelRenderer = WITHOUT_LABEL
        }
    }

    functionLikeBodyRenderer = object : KaFunctionLikeBodyRenderer {
        override fun renderBody(
            analysisSession: KaSession,
            symbol: KaFunctionSymbol,
            printer: PrettyPrinter,
        ) = printer {
            append(" {...}")
        }
    }
}

private val KtValVarKeywordOwner.isVal: Boolean
    get() {
        val elementType = valOrVarKeyword?.node?.elementType
        return elementType == KtTokens.VAL_KEYWORD
    }
