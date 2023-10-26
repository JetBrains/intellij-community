// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinAnnotationTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias

internal open class ClassifierImportCandidatesProvider(
    override val positionContext: KotlinNameReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ImportCandidatesProvider(indexProvider) {
    protected open fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean = kotlinClass.canBeImported()
    protected open fun acceptsJavaClass(javaClass: PsiClass): Boolean = javaClass.canBeImported()

    context(KtAnalysisSession)
    protected open fun acceptsClassLikeSymbol(symbol: KtClassLikeSymbol): Boolean = true

    context(KtAnalysisSession)
    protected fun KtClassLikeSymbol.getExpandedClassSymbol(): KtClassOrObjectSymbol? = when (this) {
        is KtTypeAliasSymbol -> expandedType.expandedClassSymbol
        is KtClassOrObjectSymbol -> this
    }

    context(KtAnalysisSession)
    override fun collectCandidates(): List<KtClassLikeSymbol> {
        if (positionContext.explicitReceiver != null) return emptyList()

        val unresolvedName = positionContext.getName()
        val fileSymbol = getFileSymbol()

        return buildList {
            addAll(indexProvider.getKotlinClassesByName(unresolvedName, ::acceptsKotlinClass))
            addAll(indexProvider.getJavaClassesByName(unresolvedName, ::acceptsJavaClass))
        }.filter { it.isVisible(fileSymbol) && it.classIdIfNonLocal != null && acceptsClassLikeSymbol(it) }
    }
}

internal class AnnotationImportCandidatesProvider(
    override val positionContext: KotlinAnnotationTypeNameReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ClassifierImportCandidatesProvider(positionContext, indexProvider) {
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

    context(KtAnalysisSession)
    override fun acceptsClassLikeSymbol(symbol: KtClassLikeSymbol): Boolean =
        symbol.getExpandedClassSymbol()?.classKind == KtClassKind.ANNOTATION_CLASS
}

internal class ConstructorReferenceImportCandidatesProvider(
    override val positionContext: KotlinCallableReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ClassifierImportCandidatesProvider(positionContext, indexProvider) {
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


    context(KtAnalysisSession)
    override fun acceptsClassLikeSymbol(symbol: KtClassLikeSymbol): Boolean =
        symbol.getExpandedClassSymbol()?.classKind == KtClassKind.CLASS
}
