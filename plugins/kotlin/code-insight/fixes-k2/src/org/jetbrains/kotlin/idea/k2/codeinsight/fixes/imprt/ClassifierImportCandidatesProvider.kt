// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinAnnotationTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias

internal open class ClassifierImportCandidatesProvider(
    positionContext: KotlinNameReferencePositionContext,
) : ImportCandidatesProvider(positionContext) {

    protected open fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean =
        !kotlinClass.isImported() && kotlinClass.canBeImported()

    protected open fun acceptsJavaClass(javaClass: PsiClass): Boolean =
        !javaClass.isImported() && javaClass.canBeImported()

    context(KaSession)
    protected open fun acceptsClassLikeSymbol(symbol: KaClassLikeSymbol): Boolean = true

    context(KaSession)
    protected fun KaClassLikeSymbol.getExpandedClassSymbol(): KaClassSymbol? = when (this) {
        is KaTypeAliasSymbol -> expandedType.expandedSymbol
        is KaClassSymbol -> this
    }

    context(KaSession)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<KaClassLikeSymbol> {
        if (positionContext.explicitReceiver != null) return emptyList()

        val unresolvedName = positionContext.getName()
        val fileSymbol = getFileSymbol()

        return buildList {
            addAll(indexProvider.getKotlinClassesByName(unresolvedName) { acceptsKotlinClass(it) })
            addAll(indexProvider.getJavaClassesByName(unresolvedName) { acceptsJavaClass(it) })
        }.filter { it.isVisible(fileSymbol) && it.classId != null && acceptsClassLikeSymbol(it) }
    }
}

internal class AnnotationImportCandidatesProvider(
    positionContext: KotlinAnnotationTypeNameReferencePositionContext,
) : ClassifierImportCandidatesProvider(positionContext) {

    override fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean {
        val isPossiblyAnnotation = when (kotlinClass) {
            is KtTypeAlias -> true
            is KtClassOrObject -> kotlinClass.isAnnotation()
            else -> false
        }

        return isPossiblyAnnotation && super.acceptsKotlinClass(kotlinClass)
    }

    override fun acceptsJavaClass(javaClass: PsiClass): Boolean =
        javaClass.isAnnotationType && super.acceptsJavaClass(javaClass)

    context(KaSession)
    override fun acceptsClassLikeSymbol(symbol: KaClassLikeSymbol): Boolean =
        symbol.getExpandedClassSymbol()?.classKind == KaClassKind.ANNOTATION_CLASS
}

internal class ConstructorReferenceImportCandidatesProvider(
    positionContext: KotlinCallableReferencePositionContext,
) : ClassifierImportCandidatesProvider(positionContext) {

    override fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean {
        val possiblyHasAcceptableConstructor = when (kotlinClass) {
            is KtTypeAlias -> true
            is KtClass -> !(kotlinClass.isEnum() || kotlinClass.isInterface() || kotlinClass.isAnnotation())
            else -> false
        }

        return possiblyHasAcceptableConstructor && super.acceptsKotlinClass(kotlinClass)
    }

    override fun acceptsJavaClass(javaClass: PsiClass): Boolean =
        !(javaClass.isEnum || javaClass.isInterface || javaClass.isAnnotationType) && super.acceptsJavaClass(javaClass)


    context(KaSession)
    override fun acceptsClassLikeSymbol(symbol: KaClassLikeSymbol): Boolean =
        symbol.getExpandedClassSymbol()?.classKind == KaClassKind.CLASS
}
