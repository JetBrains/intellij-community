// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinAnnotationTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject

internal open class ClassifierImportCandidatesProvider(
    override val positionContext: KotlinNameReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ImportCandidatesProvider(indexProvider) {
    protected open fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean = kotlinClass.canBeImported()
    protected open fun acceptsJavaClass(javaClass: PsiClass): Boolean = javaClass.canBeImported()

    context(KtAnalysisSession)
    override fun collectCandidates(): List<KtClassLikeSymbol> {
        if (positionContext.explicitReceiver != null) return emptyList()

        val unresolvedName = positionContext.getName()
        val fileSymbol = getFileSymbol()

        return buildList {
            addAll(indexProvider.getKotlinClassesByName(unresolvedName, ::acceptsKotlinClass))
            addAll(indexProvider.getJavaClassesByName(unresolvedName, ::acceptsJavaClass))
        }.filter { it.isVisible(fileSymbol) && it.classIdIfNonLocal != null }
    }
}

internal class AnnotationImportCandidatesProvider(
    override val positionContext: KotlinAnnotationTypeNameReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ClassifierImportCandidatesProvider(positionContext, indexProvider) {
    override fun acceptsKotlinClass(kotlinClass: KtClassLikeDeclaration): Boolean =
        kotlinClass is KtClassOrObject && kotlinClass.isAnnotation() && super.acceptsKotlinClass(kotlinClass)

    override fun acceptsJavaClass(javaClass: PsiClass): Boolean =
        javaClass.isAnnotationType && super.acceptsJavaClass(javaClass)
}
