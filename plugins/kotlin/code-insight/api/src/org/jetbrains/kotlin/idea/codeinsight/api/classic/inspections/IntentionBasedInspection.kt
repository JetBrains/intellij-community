// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.reflect.KClass

// This class was originally created to make possible switching inspection off and using intention instead.
// Since IDEA 2017.1, it's possible to have inspection severity "No highlighting, only fix"
// thus making the original purpose useless.
// The class still can be used, if you want to create a pair for existing intention with additional checker
abstract class IntentionBasedInspection<TElement : PsiElement> private constructor(
    private val intentionInfo: IntentionData<TElement>,
    protected open val problemText: String?
) : AbstractKotlinInspection() {

    val intention: SelfTargetingRangeIntention<TElement> by lazy {
        val intentionClass = intentionInfo.intention
        intentionClass.constructors.single { it.parameters.isEmpty() }.call().apply {
            inspection = this@IntentionBasedInspection
        }
    }

    @Deprecated("Please do not use for new inspections. Use AbstractKotlinInspection as base class for them")
    constructor(
        intention: KClass<out SelfTargetingRangeIntention<TElement>>,
        problemText: String? = null
    ) : this(IntentionData(intention), problemText)

    constructor(
        intention: KClass<out SelfTargetingRangeIntention<TElement>>,
        additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean,
        problemText: String? = null
    ) : this(IntentionData(intention, additionalChecker), problemText)

    constructor(
        intention: KClass<out SelfTargetingRangeIntention<TElement>>,
        additionalChecker: (TElement) -> Boolean,
        problemText: String? = null
    ) : this(IntentionData(intention) { element, _ -> additionalChecker(element) }, problemText)


    data class IntentionData<TElement : PsiElement>(
        val intention: KClass<out SelfTargetingRangeIntention<TElement>>,
        val additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean = { _, _ -> true }
    )

    open fun additionalFixes(element: TElement): List<LocalQuickFix>? = null

    open fun inspectionTarget(element: TElement): PsiElement? = null

    @InspectionMessage
    open fun inspectionProblemText(element: TElement): String? = null

    private fun PsiElement.toRange(baseElement: PsiElement): TextRange {
        val start = getStartOffsetIn(baseElement)
        return TextRange(start, start + endOffset - startOffset)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {

        val elementType = intention.elementType
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (!elementType.isInstance(element) || element.textLength == 0) return

                @Suppress("UNCHECKED_CAST")
                val targetElement = element as TElement

                var problemRange: TextRange? = null
                val fixes = SmartList<LocalQuickFix>()

                val additionalChecker = intentionInfo.additionalChecker
                run {
                    val range = intention.applicabilityRange(targetElement)?.let { range ->
                        val elementRange = targetElement.textRange
                        assert(range in elementRange) { "Wrong applicabilityRange() result for $intention - should be within element's range" }
                        range.shiftRight(-elementRange.startOffset)
                    }

                    if (range != null && additionalChecker(targetElement, this@IntentionBasedInspection)) {
                        problemRange = range
                        fixes.add(createQuickFix(intention, additionalChecker, targetElement))
                    }
                }

                val range = inspectionTarget(targetElement)?.toRange(element) ?: problemRange
                if (range != null) {
                    additionalFixes(targetElement)?.let { fixes.addAll(it) }
                    if (!fixes.isEmpty()) {
                        holder.registerProblemWithoutOfflineInformation(
                            targetElement,
                            inspectionProblemText(element) ?: problemText ?: fixes.first().name,
                            isOnTheFly,
                            problemHighlightType(targetElement),
                            range,
                            *fixes.toTypedArray()
                        )
                    }
                }
            }
        }
    }

    protected open fun problemHighlightType(element: TElement): ProblemHighlightType =
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    private fun createQuickFix(
        intention: SelfTargetingRangeIntention<TElement>,
        additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean,
        targetElement: TElement
    ): IntentionBasedQuickFix = when (intention) {
        is LowPriorityAction -> LowPriorityIntentionBasedQuickFix(intention, additionalChecker, targetElement)
        is HighPriorityAction -> HighPriorityIntentionBasedQuickFix(intention, additionalChecker, targetElement)
        else -> IntentionBasedQuickFix(intention, additionalChecker, targetElement)
    }

    /* we implement IntentionAction to provide isAvailable which will be used to hide outdated items and make sure we never call 'invoke' for such item */
    internal open inner class IntentionBasedQuickFix(
        val intention: SelfTargetingRangeIntention<TElement>,
        private val additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean,
        targetElement: TElement
    ) : LocalQuickFixOnPsiElement(targetElement), IntentionAction {

        private val text = intention.text

        // store text into variable because intention instance is shared and may change its text later
        override fun getFamilyName() = intention.familyName

        override fun getText(): String = text

        override fun startInWriteAction() = intention.startInWriteAction()

        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = isAvailable

        override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
            assert(startElement == endElement)
            @Suppress("UNCHECKED_CAST")
            return intention.applicabilityRange(startElement as TElement) != null && additionalChecker(
                startElement,
                this@IntentionBasedInspection
            )
        }

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            applyFix()
        }

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            assert(startElement == endElement)
            if (!isAvailable(project, file, startElement, endElement)) return
            if (!IntentionPreviewUtils.prepareElementForWrite(file)) return

            val editor = startElement.findExistingEditor()
            editor?.caretModel?.moveToOffset(startElement.textOffset)
            @Suppress("UNCHECKED_CAST")
            intention.applyTo(startElement as TElement, editor)
        }

        @Suppress("UNCHECKED_CAST")
        override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
            val newIntention = intention.getFileModifierForPreview(target) as? SelfTargetingRangeIntention<TElement> ?: return null
            val newElement = PsiTreeUtil.findSameElementInCopy(startElement, target) as? TElement ?: return null
            return IntentionBasedQuickFix(newIntention, additionalChecker, newElement)
        }
    }

    private inner class LowPriorityIntentionBasedQuickFix(
        intention: SelfTargetingRangeIntention<TElement>,
        additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean,
        targetElement: TElement
    ) : IntentionBasedQuickFix(intention, additionalChecker, targetElement), LowPriorityAction

    private inner class HighPriorityIntentionBasedQuickFix(
        intention: SelfTargetingRangeIntention<TElement>,
        additionalChecker: (TElement, IntentionBasedInspection<TElement>) -> Boolean,
        targetElement: TElement
    ) : IntentionBasedQuickFix(intention, additionalChecker, targetElement), HighPriorityAction
}

