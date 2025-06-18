// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiFilePattern
import com.intellij.polySymbols.context.PolyContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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

fun interface PolySymbolLocationQueryScopeProvider<T : PsiElement?> {

  fun getScopes(location: T): List<PolySymbolScope>

}

fun interface PolySymbolProjectQueryScopeProvider {

  fun getScopes(project: Project): List<PolySymbolScope>

}

interface PolySymbolQueryScopeProviderRegistrarInFileContext {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun withoutResolve(): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun forAnyPsiLocation(): PolySymbolLocationQueryScopeProviderRegistrar<PsiElement>

  fun <T : PsiElement> forPsiLocation(psiLocationClass: Class<T>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocation(psiLocationPattern: PsiElementPattern<T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(vararg psiLocationPatterns: PsiElementPattern<out T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(vararg psiLocationClasses: Class<out T>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(psiLocationPatterns: Collection<PsiElementPattern<out T, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T>
}

interface PolySymbolQueryScopeProviderRegistrar: PolySymbolQueryScopeProviderRegistrarInFileContext {

  fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFile(fileClass: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolQueryScopeProviderRegistrarInFileContext

  override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrar

  override fun withoutResolve(): PolySymbolQueryScopeProviderRegistrar

  fun forProject(): PolySymbolProjectQueryScopeProviderRegistrar

}

interface PolySymbolProjectQueryScopeProviderRegistrar {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolProjectQueryScopeProviderRegistrar

  fun withoutResolve(): PolySymbolProjectQueryScopeProviderRegistrar

  fun contributeScopeProvider(provider: PolySymbolProjectQueryScopeProvider)

}

interface PolySymbolLocationQueryScopeProviderRegistrar<T: PsiElement> {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun withoutResolve(): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFile(fileClass: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun contributeScopeProvider(provider: PolySymbolLocationQueryScopeProvider<T>)

}

