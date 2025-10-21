// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.quickfix.AddWhenRemainingBranchesFix
import org.jetbrains.kotlin.psi.KtWhenExpression

class AddWhenRemainingBranchesIntention : SelfTargetingIntention<KtWhenExpression>(
    KtWhenExpression::class.java, KotlinBundle.messagePointer("add.remaining.branches")
) {
    override fun isApplicableTo(element: KtWhenExpression, caretOffset: Int): Boolean {
        return AddWhenRemainingBranchesFix.isAvailable(element)
    }

    override fun applyTo(element: KtWhenExpression, editor: Editor?) {
        AddWhenRemainingBranchesFix.addRemainingBranches(element)
    }
}