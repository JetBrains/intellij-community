// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.patterns.PsiElementPattern
import com.intellij.polySymbols.context.PolyContext
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate

interface PolySymbolQueryScopeContributor {

  fun registerProviders(registrar: PolySymbolQueryScopeProviderRegistrar)

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolQueryScopeContributor> =
      ExtensionPointName<PolySymbolQueryScopeContributor>("com.intellij.polySymbols.queryScopeContributor")

  }

}

fun interface PolySymbolQueryScopeProvider<T: PsiElement?> {

  fun getScopes(location: T): List<PolySymbolScope>

}
fun interface PolySymbolProjectQueryScopeProvider {

  fun getScopes(project: Project): List<PolySymbolScope>

}

interface PolySymbolQueryScopeProviderRegistrar {

  fun <T: PsiElement> forPsiLocation(psiLocationPattern: PsiElementPattern.Capture<T>): Builder<PolySymbolQueryScopeProvider<T>>

  fun forAnywhere(): Builder<PolySymbolProjectQueryScopeProvider>

  interface Builder <P> {

    fun inContext(contextFilter: Predicate<PolyContext>): Builder<P>

    fun withoutResolve(): Builder<P>

    fun contributeScopeProvider(provider: P)

  }

}

