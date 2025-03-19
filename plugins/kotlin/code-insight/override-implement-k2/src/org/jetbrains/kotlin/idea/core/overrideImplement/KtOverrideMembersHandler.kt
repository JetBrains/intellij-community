// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.symbols.*
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

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun collectMembers(classOrObject: KtClassOrObject): List<KtClassMember> {
    val classSymbol = (classOrObject.symbol as? KaEnumEntrySymbol)?.enumEntryInitializer as? KaClassSymbol ?: classOrObject.classSymbol ?: return emptyList()
    return getOverridableMembers(classSymbol).map { (symbol, bodyType, containingSymbol) ->
        @NlsSafe
        val fqName = containingSymbol?.classId?.asSingleFqName()?.toString() ?: containingSymbol?.name?.asString()
        KtClassMember(
            KtClassMemberInfo.create(
                symbol,
                symbol.render(renderer),
                getIcon(symbol),
                fqName,
                containingSymbol?.let { getIcon(it) },
            ),
            bodyType,
            preferConstructorParameter = false,
        )
    }
}


    context(KaSession)
    fun noConcreteDirectOverriddenSymbol(symbol: KaCallableSymbol): Boolean {
        fun isConcreteFunction(superSymbol: KaCallableSymbol): Boolean {
            if (superSymbol.modality == KaSymbolModality.ABSTRACT) return false
            if ((superSymbol.containingSymbol as? KaNamedClassSymbol)?.classId != StandardClassIds.Any) return true
            return (superSymbol as? KaNamedFunctionSymbol)?.name?.asString() !in listOf("equals", "hashCode")
        }

        return symbol.directlyOverriddenSymbols.none { isConcreteFunction(it) }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getOverridableMembers(classOrObjectSymbol: KaClassSymbol): List<OverrideMember> {
        return buildList {
            classOrObjectSymbol.memberScope.callables.forEach { symbol ->
                if (!symbol.isVisibleInClass(classOrObjectSymbol)) return@forEach
                val implementationStatus = symbol.getImplementationStatus(classOrObjectSymbol) ?: return@forEach
                if (!implementationStatus.isOverridable) return@forEach

                val intersectionSymbols = symbol.intersectionOverriddenSymbols
                val symbolsToProcess = if (intersectionSymbols.size <= 1) {
                    listOf(symbol)
                } else {
                    val nonAbstractMembers = intersectionSymbols.filter { it.modality != KaSymbolModality.ABSTRACT }
                    // If there are non-abstract members, we only want to show override for these non-abstract members. Otherwise, show any
                    // abstract member to override.
                    nonAbstractMembers.ifEmpty {
                        listOf(intersectionSymbols.first())
                    }
                }

                val hasNoSuperTypesExceptAny = classOrObjectSymbol.superTypes.singleOrNull()?.isAnyType == true
                for (symbolToProcess in symbolsToProcess) {
                    val originalOverriddenSymbol = symbolToProcess.fakeOverrideOriginal
                    val containingSymbol = originalOverriddenSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol

                    val bodyType = when {
                        classOrObjectSymbol.classKind == KaClassKind.INTERFACE && containingSymbol?.classId == StandardClassIds.Any -> {
                            if (hasNoSuperTypesExceptAny) {
                                // If an interface does not extends any other interfaces, FE1.0 simply skips members of `Any`. So we mimic
                                // the same behavior. See idea/testData/codeInsight/overrideImplement/noAnyMembersInInterface.kt
                                continue
                            } else {
                                BodyType.NoBody
                            }
                        }
                        (classOrObjectSymbol as? KaNamedClassSymbol)?.isInline == true &&
                                containingSymbol?.classId == StandardClassIds.Any -> {
                            if ((symbolToProcess as? KaNamedFunctionSymbol)?.name?.asString() in listOf("equals", "hashCode")) {
                                continue
                            } else {
                                BodyType.Super
                            }
                        }
                        originalOverriddenSymbol.modality == KaSymbolModality.ABSTRACT && noConcreteDirectOverriddenSymbol(symbolToProcess) ->
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
        val symbol: KaCallableSymbol,
        val bodyType: BodyType,
        val containingSymbol: KaClassSymbol?,
        override val token: KaLifetimeToken
    ) : KaLifetimeOwner

    override fun getChooserTitle() = KotlinIdeaCoreBundle.message("override.members.handler.title")

    override fun getNoMembersFoundHint() = KotlinIdeaCoreBundle.message("override.members.handler.no.members.hint")
}