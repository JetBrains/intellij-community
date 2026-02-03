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

/**
 * Implement this interface and register through `com.intellij.polySymbols.queryScopeContributor`
 * extension point to provide [PolySymbolScope]s for [PolySymbolQueryExecutor] to work with.
 *
 * The concept is similar to how code completion works. Platform runs multiple code completion
 * contributors to build a list of available items for a particular place in the code. Later on,
 * the list is displayed to the user. Query executor, on the other hand, uses a list of contributed
 * [PolySymbolScope]s at a given location to either list all available symbols,
 * get code completions or match a name against the list of available symbols.
 *
 * The implementations should register query scope providers by calling the `registrar` parameter
 * methods. The registrar works as a builder, but each call creates a new instance, so you can reuse
 * build stages, e.g.:
 *
 * ```kotlin
 * registrar
 *   .inFile(AstroFileImpl::class.java)
 *   .inContext { it.framework == AstroFramework.ID }
 *   .apply {
 *     // Default scopes
 *     forAnyPsiLocationInFile()
 *       .contributeScopeProvider {
 *         mutableListOf(AstroFrontmatterScope(it.containingFile as AstroFileImpl),
 *                       AstroAvailableComponentsScope(it.project))
 *       }
 *
 *     // AstroStyleDefineVarsScope
 *     forPsiLocation(CssElement::class.java)
 *       .contributeScopeProvider { location ->
 *         location.parentOfType<XmlTag>()
 *           ?.takeIf { StringUtil.equalsIgnoreCase(it.name, HtmlUtil.STYLE_TAG_NAME) }
 *           ?.let { listOf(AstroStyleDefineVarsScope(it)) }
 *         ?: emptyList()
 *       }
 *   }
 * ```
 *
 * The order in which the builder methods are called does not affect the final performance.
 * Framework reorders conditions in the best way to efficiently match contributors with locations in the code.
 */
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

fun interface PolySymbolAnyQueryScopeProvider {

  fun getScopes(project: Project, location: PsiElement?): List<PolySymbolScope>

}

interface PolySymbolQueryScopeProviderRegistrarInFileContext {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun withResolveRequired(): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun forAnyPsiLocationInFile(): PolySymbolLocationQueryScopeProviderRegistrar<PsiElement>

  fun <T : PsiElement> forPsiLocation(psiLocationClass: Class<T>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocation(psiLocationPattern: PsiElementPattern<T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(vararg psiLocationPatterns: PsiElementPattern<out T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(vararg psiLocationClasses: Class<out T>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun <T : PsiElement> forPsiLocations(psiLocationPatterns: Collection<PsiElementPattern<out T, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T>
}

interface PolySymbolQueryScopeProviderRegistrar : PolySymbolQueryScopeProviderRegistrarInFileContext {

  fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFile(fileClass: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext

  fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolQueryScopeProviderRegistrarInFileContext

  override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrar

  override fun withResolveRequired(): PolySymbolQueryScopeProviderRegistrar

  fun forAnywhere(): PolySymbolAnyQueryScopeProviderRegistrar

}

interface PolySymbolAnyQueryScopeProviderRegistrar {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolAnyQueryScopeProviderRegistrar

  fun withResolveRequired(): PolySymbolAnyQueryScopeProviderRegistrar

  fun contributeScopeProvider(provider: PolySymbolAnyQueryScopeProvider)

}

interface PolySymbolLocationQueryScopeProviderRegistrar<T : PsiElement> {

  fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun withResolveRequired(): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFile(fileClass: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T>

  fun contributeScopeProvider(provider: PolySymbolLocationQueryScopeProvider<T>)

}

