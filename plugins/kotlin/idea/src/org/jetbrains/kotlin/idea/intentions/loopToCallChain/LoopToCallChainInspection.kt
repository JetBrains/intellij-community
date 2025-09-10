// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.sequence.AsSequenceTransformation
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.util.function.Supplier

class LoopToCallChainInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            val nonLazyIntention = LoopToCallChainIntention()
            val lazyIntention = LoopToLazyCallChainIntention()

            override fun visitForExpression(expression: KtForExpression) {
                super.visitForExpression(expression)

                val nonLazyApplicable = nonLazyIntention.applicabilityRange(expression) != null
                val lazyApplicable = lazyIntention.applicabilityRange(expression) != null

                if (!nonLazyApplicable && !lazyApplicable) return

                val fixes = mutableListOf<Fix>()
                if (nonLazyApplicable) {
                    fixes += Fix(lazy = false, text = nonLazyIntention.text)
                }
                if (lazyApplicable) {
                    fixes += Fix(lazy = true, text = lazyIntention.text)
                }

                holder.registerProblem(
                    expression.forKeyword,
                    KotlinBundle.message("loop.can.be.replaced.with.stdlib.operations"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *fixes.toTypedArray()
                )
            }
        }

    class Fix(val lazy: Boolean, @Nls val text: String = "") : LocalQuickFix {
        override fun getFamilyName(): String {
            return if (lazy) {
                KotlinBundle.message("loop.to.call.fix.family.name2")
            } else {
                KotlinBundle.message("loop.to.call.fix.family.name")
            }
        }

        override fun getName() = text

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement.getParentOfType<KtForExpression>(strict = true) ?: return
            applyFix(expression)
        }

        fun applyFix(expression: KtForExpression, editor: Editor? = expression.findExistingEditor()) {
            val match = match(expression, lazy, true) ?: return
            val offset = when (val result = convertLoop(expression, match)) {
                // if result is variable declaration, put the caret onto its name to allow quick inline
                is KtProperty -> result.nameIdentifier?.startOffset ?: result.startOffset
                else -> result.startOffset
            }

            editor?.moveCaret(offset)
        }
    }
}

internal class LoopToCallChainIntention : AbstractLoopToCallChainIntention(
    lazy = false,
    textGetter = KotlinBundle.messagePointer("replace.with.stdlib.operations")
)

internal class LoopToLazyCallChainIntention : AbstractLoopToCallChainIntention(
    lazy = true,
    textGetter = KotlinBundle.messagePointer("replace.with.stdlib.operations.with.use.of.assequence")
)

internal abstract class AbstractLoopToCallChainIntention(
    private val lazy: Boolean,
    textGetter: Supplier<@IntentionName String>,
) : SelfTargetingRangeIntention<KtForExpression>(
    KtForExpression::class.java,
    textGetter
) {
    override fun applicabilityRange(element: KtForExpression): TextRange? {
        val match = match(element, lazy, false)
        setTextGetter(
            if (match != null)
                KotlinBundle.messagePointer("replace.with.0", match.transformationMatch.buildPresentation())
            else
                defaultTextGetter
        )

        return if (match != null) element.forKeyword.textRange else null
    }

    private fun TransformationMatch.Result.buildPresentation(): String =
        buildPresentation(sequenceTransformations + resultTransformation, null)

    private fun buildPresentation(transformations: List<Transformation>, prevPresentation: String?): String {
        if (transformations.size > MAX) {
            if (transformations[0] is AsSequenceTransformation) {
                return buildPresentation(transformations.drop(1), transformations[0].presentation)
            }

            return buildPresentation(transformations.drop(transformations.size - MAX), prevPresentation?.let { "$it.." } ?: "..")
        }

        var result: String? = prevPresentation
        for (transformation in transformations) {
            result = transformation.buildPresentation(result)
        }

        return result!!
    }

    override fun applyTo(element: KtForExpression, editor: Editor?) {
        LoopToCallChainInspection.Fix(lazy).applyFix(element, editor)
    }
}

private const val MAX: Int = 3
