// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val LOG = Logger.getInstance(AbstractApplicabilityBasedInspection::class.java.name)
abstract class AbstractApplicabilityBasedInspection<TElement : KtElement>(
    val elementType: Class<TElement>
) : AbstractKotlinInspection() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                if (!elementType.isInstance(element) || element.textLength == 0) return
                @Suppress("UNCHECKED_CAST")
                visitTargetElement(element as TElement, holder, isOnTheFly)
            }
        }

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
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
        val description = inspectionText(element)
        val range = inspectionHighlightRangeInElement(element)
        if (LOG.isDebugEnabled) {
            val existingDescriptor = holder.results.find {
                it.psiElement == element && it.descriptionTemplate == description && it.textRangeInElement == range
            }

            if (existingDescriptor != null) {
                LOG.debug("Duplicated problem registered for $element in $range with text $description")
                return
            }
        }

        holder.registerProblemWithoutOfflineInformation(
            element,
            description,
            isOnTheFly,
            inspectionHighlightType(element),
            range,
            createQuickFix(element),
        )
    }

    open fun inspectionHighlightRangeInElement(element: TElement): TextRange? = null

    open fun inspectionHighlightType(element: TElement): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    @InspectionMessage
    abstract fun inspectionText(element: TElement): String

    abstract val defaultFixText: String

    @IntentionName
    open fun fixText(element: TElement): String = defaultFixText

    abstract fun isApplicable(element: TElement): Boolean

    abstract fun applyTo(element: TElement, project: Project = element.project, editor: Editor? = null)

    open val startFixInWriteAction: Boolean = true

    open fun createQuickFix(element: TElement): LocalQuickFix {
        return LocalFix(this, fixText(element))
    }

    private class LocalFix<TElement : KtElement>(
      @FileModifier.SafeFieldForPreview val inspection: AbstractApplicabilityBasedInspection<TElement>,
      @IntentionName val text: String
    ) : LocalQuickFix, ReportingClassSubstitutor {
        override fun startInWriteAction() = inspection.startFixInWriteAction

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            @Suppress("UNCHECKED_CAST")
            val element = descriptor.psiElement as TElement
            inspection.applyTo(element, project, element.findExistingEditor())
        }

        override fun getFamilyName() = inspection.defaultFixText

        override fun getName() = text

        override fun getSubstitutedClass(): Class<*> = inspection.javaClass
    }
}