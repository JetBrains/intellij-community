// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain

import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

@Suppress("DEPRECATION")
class UseWithIndexInspection : IntentionBasedInspection<KtForExpression>(UseWithIndexIntention::class)

class UseWithIndexIntention : SelfTargetingRangeIntention<KtForExpression>(
    KtForExpression::class.java,
    KotlinBundle.messagePointer("use.withindex.instead.of.manual.index.increment")
) {

    override fun startInWriteAction(): Boolean  = false

    override fun applicabilityRange(element: KtForExpression): TextRange? =
        if (matchIndexToIntroduce(element, reformat = false) != null) element.forKeyword.textRange else null

    override fun applyTo(element: KtForExpression, editor: Editor?) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return
        val (indexVariable, initializationStatement, incrementExpression) = matchIndexToIntroduce(element, reformat = true)!!

        val psiFactory = KtPsiFactory(element.project)
        val loopRange = element.loopRange!!
        val loopParameter = element.loopParameter!!

        runWriteAction {
            val newLoopRange = psiFactory.createExpressionByPattern("$0.withIndex()", loopRange)
            loopRange.replace(newLoopRange)

            val multiParameter = (psiFactory.createExpressionByPattern(
                "for(($0, $1) in x){}",
                indexVariable.nameAsSafeName,
                loopParameter.text
            ) as KtForExpression).loopParameter!!
            loopParameter.replace(multiParameter)

            initializationStatement.delete()
            if (incrementExpression.parent is KtBlockExpression) {
                incrementExpression.delete()
            } else {
                removePlusPlus(incrementExpression, true)
            }
        }
    }
}