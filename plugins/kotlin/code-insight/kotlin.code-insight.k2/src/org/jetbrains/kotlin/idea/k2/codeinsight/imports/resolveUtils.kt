// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Finds the original SAM type by the [samConstructorSymbol].
 *
 * A workaround for the KT-70301.
 */
internal fun KaSession.findSamClassFor(samConstructorSymbol: KaSamConstructorSymbol): KaClassSymbol? {
    val samCallableId = samConstructorSymbol.callableId ?: return null
    if (samCallableId.isLocal) return null

    val samClassId = ClassId.fromString(samCallableId.toString())

    return findClass(samClassId)
}

/**
 * Takes a [reference] pointing to a typealiased constructor call like `FooAlias()`,
 * and [expandedClassSymbol] pointing to the expanded class `Foo`.
 *
 * Returns a `FooAlias` typealias symbol if it is resolvable at this position, and `null` otherwise.
 *
 * This is a workaround function until KTIJ-26098 is fixed.
 */
internal fun KaSession.resolveTypeAliasedConstructorReference(
    reference: KtReference,
    expandedClassSymbol: KaClassLikeSymbol,
    containingFile: KtFile,
): KaClassLikeSymbol? {
    val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

    // optimization to avoid resolving typealiases which are not available
    if (!typeAliasIsAvailable(originalReferenceName, containingFile)) return null

    val referencedType = resolveReferencedType(reference) ?: return null
    if (referencedType.symbol != expandedClassSymbol) return null

    val typealiasType = referencedType.abbreviation ?: return null

    return typealiasType.symbol
}

private fun KaSession.typeAliasIsAvailable(name: Name, containingFile: KtFile): Boolean {
    val importingScope = containingFile.importingScopeContext
    val foundClassifiers = importingScope.compositeScope().classifiers(name)

    return foundClassifiers.any { it is KaTypeAliasSymbol }
}

private fun KaSession.resolveReferencedType(reference: KtReference): KaType? {
    val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

    val psiFactory = KtPsiFactory.contextual(reference.element)
    val psiType = psiFactory.createTypeCodeFragment(originalReferenceName.asString(), context = reference.element).getContentElement()

    return psiType?.type
}
