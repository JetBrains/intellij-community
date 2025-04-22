// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.utils.errors.requireIsInstance
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeAlias

internal open class ClassifierImportCandidatesProvider(
    override val importContext: ImportContext,
) : AbstractImportCandidatesProvider() {

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
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ClassLikeImportCandidate> {
        if (importContext.isExplicitReceiver) return emptyList()

        val fileSymbol = getFileSymbol()
        val visibilityChecker = createUseSiteVisibilityChecker(fileSymbol, receiverExpression = null, importContext.position)

        return buildList {
            addAll(indexProvider.getKotlinClassesByName(name) { acceptsKotlinClass(it) })
            addAll(indexProvider.getJavaClassesByName(name) { acceptsJavaClass(it) })
        }
            .map { ClassLikeImportCandidate(it) }
            .filter { it.classId != null && it.isVisible(visibilityChecker) && acceptsClassLikeSymbol(it.symbol) }
    }
}

internal class AnnotationImportCandidatesProvider(
    importContext: ImportContext,
) : ClassifierImportCandidatesProvider(importContext) {

    init {
        requireIsInstance<ImportPositionType.Annotation>(importContext.positionType)
    }

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
    importContext: ImportContext,
) : ClassifierImportCandidatesProvider(importContext) {

    init {
        requireIsInstance<ImportPositionType.CallableReference>(importContext.positionType)
    }

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
