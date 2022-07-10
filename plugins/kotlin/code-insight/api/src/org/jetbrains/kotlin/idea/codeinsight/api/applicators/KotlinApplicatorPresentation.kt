// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager

/**
 * Provides a presentation to display an message in the editor which may be latter fixed by [KotlinApplicator]
 */
sealed class KotlinApplicatorPresentation<PSI : PsiElement> {
    fun getHighlightType(element: PSI): ProblemHighlightType = KtAnalysisAllowanceManager.forbidAnalysisInside("KotlinApplicatorPresentation.getHighlightType") {
        getHighlightTypeImpl(element)
    }

    abstract fun getHighlightTypeImpl(element: PSI): ProblemHighlightType
}

private class KotlinApplicatorPresentationImpl<PSI : PsiElement>(
    private val getHighlightType: (element: PSI) -> ProblemHighlightType,
) : KotlinApplicatorPresentation<PSI>() {
    override fun getHighlightTypeImpl(element: PSI): ProblemHighlightType =
        getHighlightType.invoke(element)
}


class KotlinApplicatorPresentationProviderBuilder<PSI : PsiElement> internal constructor() {
    private var getHighlightType: ((element: PSI) -> ProblemHighlightType)? = null

    fun highlightType(getType: (element: PSI) -> ProblemHighlightType) {
        getHighlightType = getType
    }

    fun highlightType(type: ProblemHighlightType) {
        getHighlightType = { type }
    }

    internal fun build(): KotlinApplicatorPresentation<PSI> {
        val getHighlightType = getHighlightType
            ?: error("Please, provide highlightType")
        return KotlinApplicatorPresentationImpl(getHighlightType)
    }
}

fun <PSI : PsiElement> presentation(
    init: KotlinApplicatorPresentationProviderBuilder<PSI>.() -> Unit
): KotlinApplicatorPresentation<PSI> =
    KotlinApplicatorPresentationProviderBuilder<PSI>().apply(init).build()


