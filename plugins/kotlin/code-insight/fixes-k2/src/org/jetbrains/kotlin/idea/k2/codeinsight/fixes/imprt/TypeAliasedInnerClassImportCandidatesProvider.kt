// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias

/**
 * Provides import candidates for type aliases that reference inner classes, checking receiver type compatibility.
 * 
 * Example:
 * ```
 * class Outer {
 *     inner class Inner
 * }
 * typealias InnerAlias = Outer.Inner
 * 
 * fun usage(outer: Outer) {
 *     outer.InnerAlias() // equivalent to `outer.Inner()`
 * }
 * ```
 */
internal class TypeAliasedInnerClassImportCandidatesProvider(importContext: ImportContext) :
    ClassifierImportCandidatesProvider(importContext) {
    override fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean =
        kotlinClass is KtTypeAlias && super.acceptsKotlinClass(kotlinClass)

    override fun acceptsJavaClass(javaClass: PsiClass): Boolean = false

    context(_: KaSession) @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ClassLikeImportCandidate> {
        val fileSymbol = getFileSymbol()
        val visibilityChecker = createUseSiteVisibilityChecker(fileSymbol, receiverExpression = null, importContext.position)
        
        val receiverTypes = importContext.receiverTypes()
        if (receiverTypes.isEmpty()) return emptyList()

        // Only search for Kotlin classes (typealiases)
        return indexProvider.getKotlinClassesByName(name) { acceptsKotlinClass(it) }
            .filter { acceptsClassLikeSymbol(it) }
            .filterIsInstance<KaTypeAliasSymbol>()
            .filter { it.canBeCalledAsInnerClassConstructor(receiverTypes) }
            .map { ClassLikeImportCandidate(it) }
            .filter { it.classId != null && it.isVisible(visibilityChecker) }
            .toList()
    }

    /**
     * Checks that [this] type alias references an inner class which can be called as a constructor with the given [receiverTypes].
     * 
     * **Important note**: It does NOT check the type compatibility of the generic parameters declared via typealiases, 
     * since it is too cumbersome to do.
     * 
     * For example: 
     * ```
     * class Outer<T> { inner class Inner }
     * 
     * typealias InnerAlias = Outer<String>.Inner
     * 
     * fun usage(outer: Outer<Int>) {
     *   outer.<caret>
     * }
     * ```
     * 
     * You cannot use `InnerAlias` at the caret due to incompatible type arguments (`Int` vs `String`). 
     * **We do NOT check for this in this function.**
     */
    context(_: KaSession)
    private fun KaTypeAliasSymbol.canBeCalledAsInnerClassConstructor(receiverTypes: List<KaType>): Boolean {
        val expandedClassSymbol = getExpandedClassSymbol() as? KaNamedClassSymbol ?: return false
        if (!expandedClassSymbol.isInner) return false

        val containingSymbol = expandedClassSymbol.containingSymbol as? KaClassSymbol ?: return false

        // Check if any receiver type is compatible with the outer class type
        return receiverTypes.any { receiverType -> receiverType.isSubtypeOf(containingSymbol) }
    }
}