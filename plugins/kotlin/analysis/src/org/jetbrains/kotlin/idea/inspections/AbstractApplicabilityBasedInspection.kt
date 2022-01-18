// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class AbstractApplicabilityBasedInspection<TElement : KtElement>(
    val elementType: Class<TElement>
) : AbstractKotlinInspection() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                if (!elementType.isInstance(element) || element.textLength == 0) return
                @Suppress("UNCHECKED_CAST")
                visitTargetElement(element as TElement, holder, isOnTheFly)
            }
        }

    // This function should be called from visitor built by a derived inspection
    protected fun visitTargetElement(element: TElement, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (!isApplicable(element)) return

        holder.registerProblemWithoutOfflineInformation(
            element,
            inspectionText(element),
            isOnTheFly,
            inspectionHighlightType(element),
            inspectionHighlightRangeInElement(element),
            LocalFix(this, fixText(element))
        )
    }

    open fun inspectionHighlightRangeInElement(element: TElement): TextRange? = null

    open fun inspectionHighlightType(element: TElement): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    @InspectionMessage
    abstract fun inspectionText(element: TElement): String

    abstract val defaultFixText: String

    @IntentionName
    open fun fixText(element: TElement) = defaultFixText

    abstract fun isApplicable(element: TElement): Boolean

    abstract fun applyTo(element: TElement, project: Project = element.project, editor: Editor? = null)

    open val startFixInWriteAction = true

    private class LocalFix<TElement : KtElement>(
        @SafeFieldForPreview val inspection: AbstractApplicabilityBasedInspection<TElement>,
        @IntentionName val text: String
    ) : LocalQuickFix {
        override fun startInWriteAction() = inspection.startFixInWriteAction

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            @Suppress("UNCHECKED_CAST")
            val element = descriptor.psiElement as TElement
            inspection.applyTo(element, project, element.findExistingEditor())
        }

        override fun getFamilyName() = inspection.defaultFixText

        override fun getName() = text
    }
}