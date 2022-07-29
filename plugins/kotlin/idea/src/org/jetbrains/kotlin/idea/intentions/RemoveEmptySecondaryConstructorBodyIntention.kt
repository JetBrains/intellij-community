// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

@Suppress("DEPRECATION")
class RemoveEmptySecondaryConstructorBodyInspection : IntentionBasedInspection<KtBlockExpression>(
    RemoveEmptySecondaryConstructorBodyIntention::class
), CleanupLocalInspectionTool

class RemoveEmptySecondaryConstructorBodyIntention : SelfTargetingOffsetIndependentIntention<KtBlockExpression>(
    KtBlockExpression::class.java,
    KotlinBundle.lazyMessage("remove.empty.constructor.body")
) {
    override fun applyTo(element: KtBlockExpression, editor: Editor?) = element.delete()

    override fun isApplicableTo(element: KtBlockExpression): Boolean {
        if (element.parent !is KtSecondaryConstructor) return false
        if (element.statements.isNotEmpty()) return false

        return element.text.replace("{", "").replace("}", "").isBlank()
    }

}