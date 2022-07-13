// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyzeWithReadAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicatorBasedIntention<PSI : KtElement, INPUT : KotlinApplicatorInput>(
    elementType: KClass<PSI>,
) : SelfTargetingIntention<PSI>(elementType.java, { "" }) {

    abstract val applicator: KotlinApplicator<PSI, INPUT>

    init {
        setFamilyNameGetter { applicator.getFamilyName() }
    }

    final override fun isApplicableTo(element: PSI, caretOffset: Int): Boolean {
        val project = element.project// TODO expensive operation, may require traversing the tree up to containing PsiFile
        if (!applicator.isApplicableByPsi(element, project)) return false
        val ranges = applicabilityRange.getApplicabilityRanges(element)
        if (ranges.isEmpty()) return false

        // An KotlinApplicabilityRange should be relative to the element, while `caretOffset` is absolute
        val relativeCaretOffset = caretOffset - element.textRange.startOffset
        if (ranges.none { it.containsOffset(relativeCaretOffset) }) return false

        val input = getInput(element)
        if (input != null && input.isValidFor(element)) {
            val actionText = applicator.getActionName(element, input)
            val familyName = applicator.getFamilyName()
            setFamilyNameGetter { familyName }
            setTextGetter { actionText }
            return true
        }
        return false
    }


    final override fun applyTo(element: PSI, project: Project, editor: Editor?) {
        val input = getInput(element) ?: return
        if (input.isValidFor(element)) {
            applicator.applyTo(element, input, project, editor)
        }
    }

    final override fun applyTo(element: PSI, editor: Editor?) {
        applyTo(element, element.project, editor)
    }


    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun getInput(element: PSI): INPUT? = allowAnalysisOnEdt {
        analyzeWithReadAction(element) {
            with(inputProvider) { provideInput(element) }
        }
    }

    abstract val applicabilityRange: KotlinApplicabilityRange<PSI>
    abstract val inputProvider: KotlinApplicatorInputProvider<PSI, INPUT>
}
