// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.KtIconProvider.getIcon
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject

@ApiStatus.Internal
open class KtOverrideMembersHandler : KtGenerateMembersHandler(false) {
    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> {
        return analyze(classOrObject) {
            collectMembers(classOrObject)
        }
    }

    private fun KtAnalysisSession.collectMembers(classOrObject: KtClassOrObject): List<KtClassMember> =
        classOrObject.getClassOrObjectSymbol()?.let { getOverridableMembers(it) }.orEmpty().map { (symbol, bodyType, containingSymbol) ->
            KtClassMember(
                KtClassMemberInfo.create(
                    symbol,
                    symbol.render(renderer),
                    getIcon(symbol),
                    containingSymbol?.classIdIfNonLocal?.asSingleFqName()?.toString() ?: containingSymbol?.name?.asString(),
                    containingSymbol?.let { getIcon(it) },
                ),
                bodyType,
                preferConstructorParameter = false,
            )
        }

    private fun KtAnalysisSession.getOverridableMembers(classOrObjectSymbol: KtClassOrObjectSymbol): List<OverrideMember> {
        return buildList {
            classOrObjectSymbol.getMemberScope().getCallableSymbols().forEach { symbol ->
                if (!symbol.isVisibleInClass(classOrObjectSymbol)) return@forEach
                val implementationStatus = symbol.getImplementationStatus(classOrObjectSymbol) ?: return@forEach
                if (!implementationStatus.isOverridable) return@forEach

                val intersectionSymbols = symbol.getIntersectionOverriddenSymbols()
                val symbolsToProcess = if (intersectionSymbols.size <= 1) {
                    listOf(symbol)
                } else {
                    val nonAbstractMembers = intersectionSymbols.filter { (it as? KtSymbolWithModality)?.modality != Modality.ABSTRACT }
                    // If there are non-abstract members, we only want to show override for these non-abstract members. Otherwise, show any
                    // abstract member to override.
                    nonAbstractMembers.ifEmpty {
                        listOf(intersectionSymbols.first())
                    }
                }

                val hasNoSuperTypesExceptAny = classOrObjectSymbol.superTypes.singleOrNull()?.isAny == true
                for (symbolToProcess in symbolsToProcess) {
                    val originalOverriddenSymbol = symbolToProcess.unwrapFakeOverrides
                    val containingSymbol = originalOverriddenSymbol.originalContainingClassForOverride

                    val bodyType = when {
                        classOrObjectSymbol.classKind == KtClassKind.INTERFACE && containingSymbol?.classIdIfNonLocal == StandardClassIds.Any -> {
                            if (hasNoSuperTypesExceptAny) {
                                // If an interface does not extends any other interfaces, FE1.0 simply skips members of `Any`. So we mimic
                                // the same behavior. See idea/testData/codeInsight/overrideImplement/noAnyMembersInInterface.kt
                                continue
                            } else {
                                BodyType.NoBody
                            }
                        }
                        (classOrObjectSymbol as? KtNamedClassOrObjectSymbol)?.isInline == true &&
                                containingSymbol?.classIdIfNonLocal == StandardClassIds.Any -> {
                            if ((symbolToProcess as? KtFunctionSymbol)?.name?.asString() in listOf("equals", "hashCode")) {
                                continue
                            } else {
                                BodyType.Super
                            }
                        }
                        (originalOverriddenSymbol as? KtSymbolWithModality)?.modality == Modality.ABSTRACT ->
                            BodyType.FromTemplate
                        symbolsToProcess.size > 1 ->
                            BodyType.QualifiedSuper
                        else ->
                            BodyType.Super
                    }
                    // Ideally, we should simply create `KtClassMember` here and remove the intermediate `OverrideMember` data class. But
                    // that doesn't work because this callback function is holding a read lock and `symbol.render(renderOption)` requires
                    // the write lock.
                    // Hence, we store the data in an intermediate `OverrideMember` data class and do the rendering later in the `map` call.
                    add(OverrideMember(symbolToProcess, bodyType, containingSymbol, token))
                }
            }
        }
    }

    private data class OverrideMember(
        val symbol: KtCallableSymbol,
        val bodyType: BodyType,
        val containingSymbol: KtClassOrObjectSymbol?,
        override val token: KtLifetimeToken
    ) : KtLifetimeOwner

    override fun getChooserTitle() = KotlinIdeaCoreBundle.message("override.members.handler.title")

    override fun getNoMembersFoundHint() = KotlinIdeaCoreBundle.message("override.members.handler.no.members.hint")
}