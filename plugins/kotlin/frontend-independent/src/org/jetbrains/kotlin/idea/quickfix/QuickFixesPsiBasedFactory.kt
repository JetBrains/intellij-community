// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class QuickFixesPsiBasedFactory<PSI : PsiElement>(
    private val classTag: KClass<PSI>,
    private val suitabilityChecker: PsiElementSuitabilityChecker<PSI>,
) : QuickFixFactory {
    fun createQuickFix(psiElement: PsiElement): List<IntentionAction> {
        checkIfPsiElementIsSupported(psiElement)
        @Suppress("UNCHECKED_CAST")
        return doCreateQuickFix(psiElement as PSI)
    }

    private fun checkIfPsiElementIsSupported(psiElement: PsiElement) {
        if (!psiElement::class.isSubclassOf(classTag)) {
            throw InvalidPsiElementTypeException(
                expectedPsiType = psiElement::class,
                actualPsiType = classTag,
                factoryName = this::class.toString()
            )
        }
        @Suppress("UNCHECKED_CAST")
        if (!suitabilityChecker.isSupported(psiElement as PSI)) {
            throw UnsupportedPsiElementException(psiElement, this::class.toString())
        }
    }

    protected abstract fun doCreateQuickFix(psiElement: PSI): List<IntentionAction>
}

inline fun <reified PSI : PsiElement> quickFixesPsiBasedFactory(
    suitabilityChecker: PsiElementSuitabilityChecker<PSI> = PsiElementSuitabilityCheckers.ALWAYS_SUITABLE,
    crossinline createQuickFix: (PSI) -> List<IntentionAction>,
) = object : QuickFixesPsiBasedFactory<PSI>(PSI::class, suitabilityChecker) {
    override fun doCreateQuickFix(psiElement: PSI): List<IntentionAction> = createQuickFix(psiElement)
}

inline fun <reified PSI : PsiElement, reified PSI2 : PsiElement> QuickFixesPsiBasedFactory<PSI>.coMap(
    suitabilityChecker: PsiElementSuitabilityChecker<PSI2> = PsiElementSuitabilityCheckers.ALWAYS_SUITABLE,
    crossinline map: (PSI2) -> PSI?
) = quickFixesPsiBasedFactory(suitabilityChecker) { psiElement ->
    val newPsi = map(psiElement) ?: return@quickFixesPsiBasedFactory emptyList()
    createQuickFix(newPsi)
}


class InvalidPsiElementTypeException(
    expectedPsiType: KClass<out PsiElement>,
    actualPsiType: KClass<out PsiElement>,
    factoryName: String,
) : Exception("PsiElement with type $expectedPsiType is expected but $actualPsiType found for $factoryName")


class UnsupportedPsiElementException(
    psiElement: PsiElement,
    factoryName: String
) : Exception("PsiElement $psiElement is unsopported for $factoryName")
