// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A patched version of [KaSession.containingDeclaration]; works as a workaround for KT-70949.
 */
internal fun KaSession.containingDeclarationPatched(symbol: KaSymbol): KaDeclarationSymbol? {
    symbol.containingDeclaration?.let { return it }

    val declarationPsi = symbol.psi

    if (declarationPsi is PsiMember) {
        val containingClass = declarationPsi.parent as? PsiClass
        containingClass?.namedClassSymbol?.let { return it }
    }

    return null
}

/**
 * Finds the original SAM type by the [samConstructorSymbol].
 *
 * A workaround for the KT-70301.
 */
internal fun KaSession.findSamClassFor(samConstructorSymbol: KaSamConstructorSymbol): KaClassLikeSymbol? {
    val samCallableId = samConstructorSymbol.callableId ?: return null
    if (samCallableId.isLocal) return null

    val samClassId = ClassId.fromString(samCallableId.toString())

    return findClass(samClassId) ?: findTypeAlias(samClassId)
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

/**
 * Checks if the given [KaCallableSymbol] is a static declaration from Java.
 */
internal fun KaCallableSymbol.isJavaStaticDeclaration(): Boolean =
    when (this) {
        is KaNamedFunctionSymbol -> isStatic
        is KaPropertySymbol -> isStatic
        is KaJavaFieldSymbol -> isStatic
        else -> false
    }
