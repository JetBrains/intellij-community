// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.psi.KtIfExpression

class FoldIfToReturnIntention : SelfTargetingRangeIntention<KtIfExpression>(
    KtIfExpression::class.java,
    KotlinBundle.messagePointer("lift.return.out.of.if.expression")
) {

    override fun applicabilityRange(element: KtIfExpression): TextRange? =
        if (BranchedFoldingUtils.canFoldToReturn(element)) element.ifKeyword.textRange else null

    override fun applyTo(element: KtIfExpression, editor: Editor?) {
        BranchedFoldingUtils.foldToReturn(element)
    }
}