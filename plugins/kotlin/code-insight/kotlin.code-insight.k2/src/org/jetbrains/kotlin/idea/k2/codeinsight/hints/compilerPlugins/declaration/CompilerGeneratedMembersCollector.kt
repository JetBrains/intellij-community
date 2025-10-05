// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asCompositeScope
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.deprecationStatus
import org.jetbrains.kotlin.analysis.api.components.staticMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

@OptIn(KaExperimentalApi::class)
internal object CompilerGeneratedMembersCollector {

    data class CompileGeneratedMember(
        val member: KaDeclarationSymbol,
        val subMembers: List<CompileGeneratedMember>,
    )

    context(_: KaSession)
    fun collect(
        symbol: KaClassSymbol,
        settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
    ): List<CompileGeneratedMember> {
        val scope = listOf(
            symbol.declaredMemberScope,
            symbol.staticMemberScope,
        ).asCompositeScope()

        return scope.declarations
            .filter { it.origin == KaSymbolOrigin.PLUGIN }
            .filter { it.shouldBeCollected(settings) }
            .distinct()
            .map { member ->
                when (member) {
                    is KaClassSymbol -> CompileGeneratedMember(member, collect(member, settings))
                    else -> CompileGeneratedMember(member, subMembers = emptyList())
                }
            }.toList()
    }

    context(_: KaSession)
    private fun KaDeclarationSymbol.shouldBeCollected(
        settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
    ): Boolean {
        when (this) {
            is KaConstructorSymbol -> {
                val owner = containingDeclaration as? KaClassSymbol
                if (owner?.classKind?.isObject == true) {
                    return false
                }
            }

            else -> {}
        }
        if (!settings.showHiddenMembers) {
            if (deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false
        }
        return true
    }
}