// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.dfa.fqNameEquals
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class RedundantAsSequenceInspection : AbstractKotlinInspection() {
    companion object {
        private val allowedSequenceFunctionFqNames = listOf(
            FqName("kotlin.sequences.asSequence"),
            FqName("kotlin.collections.asSequence")
        )
        private val terminations = collectionTerminationFunctionNames.associateWith { FqName("kotlin.sequences.$it") }
        private val transformationsAndTerminations =
            collectionTransformationFunctionNames.associateWith { FqName("kotlin.sequences.$it") } + terminations
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = qualifiedExpressionVisitor(fun(qualified) {
        val call = qualified.callExpression ?: return
        val callee = call.calleeExpression ?: return
        if (callee.text != "asSequence") return

        val context = qualified.analyze(BodyResolveMode.PARTIAL)
        if (!call.isCalling(allowedSequenceFunctionFqNames, context)) {
            return
        }
        val receiverType = qualified.receiverExpression.getType(context) ?: return
        when {
            receiverType.fqNameEquals("kotlin.sequences.Sequence") -> {
                val typeArgumentList = call.typeArgumentList
                if (typeArgumentList != null && !typeArgumentList.isRedundant()) return
                registerProblem(holder, qualified, callee)
            }
            else -> {
                if (!receiverType.isIterable(DefaultBuiltIns.Instance)) return
                val parent = qualified.getQualifiedExpressionForReceiver()
                val parentCall = parent?.callExpression ?: return
                if (!parentCall.isTermination(context)) return
                val grandParentCall = parent.getQualifiedExpressionForReceiver()?.callExpression
                if (grandParentCall?.isTransformationOrTermination(context) == true) return
                registerProblem(holder, qualified, callee)
            }
        }
    })

    private fun KtTypeArgumentList.isRedundant() =
        RemoveExplicitTypeArgumentsIntention.isApplicableTo(this, approximateFlexible = false)

    private fun registerProblem(holder: ProblemsHolder, qualified: KtQualifiedExpression, callee: KtExpression) {
        holder.registerProblem(
            qualified,
            callee.textRangeIn(qualified),
            KotlinBundle.message("inspection.redundant.assequence.call"),
            RemoveAsSequenceFix()
        )
    }

    private fun KtCallExpression.isTermination(context: BindingContext): Boolean {
        val fqName = terminations[calleeExpression?.text] ?: return false
        return isCalling(fqName, context)
    }

    private fun KtCallExpression.isTransformationOrTermination(context: BindingContext): Boolean {
        val fqName = transformationsAndTerminations[calleeExpression?.text] ?: return false
        return isCalling(fqName, context)
    }

    private class RemoveAsSequenceFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.assequence.call.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val qualified = descriptor.psiElement as? KtQualifiedExpression ?: return
            val commentSaver = CommentSaver(qualified)
            val replaced = qualified.replaced(qualified.receiverExpression)
            commentSaver.restore(replaced)
        }
    }
}