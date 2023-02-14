// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReturnSaver(val function: KtNamedFunction) {
    companion object {
        private val RETURN_KEY = Key<Unit>("RETURN_KEY")
    }

    val isEmpty: Boolean

    init {
        var hasReturn = false
        val body = function.bodyExpression!!
        body.forEachDescendantOfType<KtReturnExpression> {
            if (it.getTargetFunction(it.analyze(BodyResolveMode.PARTIAL)) == function) {
                hasReturn = true
                it.putCopyableUserData(RETURN_KEY, Unit)
            }
        }

        isEmpty = !hasReturn
    }

    private fun clear() {
        val body = function.bodyExpression!!
        body.forEachDescendantOfType<KtReturnExpression> { it.putCopyableUserData(RETURN_KEY, null) }
    }

    fun restore(lambda: KtLambdaExpression, label: Name) {
        clear()

        val psiFactory = KtPsiFactory(lambda.project)

        val lambdaBody = lambda.bodyExpression!!

        val returnToReplace = lambda.collectDescendantsOfType<KtReturnExpression>() { it.getCopyableUserData(RETURN_KEY) != null }

        for (returnExpression in returnToReplace) {
            val value = returnExpression.returnedExpression
            val replaceWith = if (value != null && returnExpression.isValueOfBlock(lambdaBody)) {
                value
            } else if (value != null) {
                psiFactory.createExpressionByPattern("return@$0 $1", label, value)
            } else {
                psiFactory.createExpressionByPattern("return@$0", label)
            }

            returnExpression.replace(replaceWith)

        }
    }

    private fun KtExpression.isValueOfBlock(inBlock: KtBlockExpression): Boolean = when (val parent = parent) {
        inBlock -> {
            this == inBlock.statements.last()
        }

        is KtBlockExpression -> {
            isValueOfBlock(parent) && parent.isValueOfBlock(inBlock)
        }

        is KtContainerNode -> {
            val owner = parent.parent
            if (owner is KtIfExpression) {
                (this == owner.then || this == owner.`else`) && owner.isValueOfBlock(inBlock)
            } else
                false
        }

        is KtWhenEntry -> {
            this == parent.expression && (parent.parent as KtWhenExpression).isValueOfBlock(inBlock)
        }
        else -> false
    }
}