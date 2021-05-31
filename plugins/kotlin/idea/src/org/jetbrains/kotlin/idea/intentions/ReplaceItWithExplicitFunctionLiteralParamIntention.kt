// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinImplicitLambdaParameter.Companion.convertImplicitItToExplicit
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinImplicitLambdaParameter.Companion.isAutoCreatedItUsage
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ReplaceItWithExplicitFunctionLiteralParamIntention : SelfTargetingOffsetIndependentIntention<KtNameReferenceExpression>(
    KtNameReferenceExpression::class.java, KotlinBundle.lazyMessage("replace.it.with.explicit.parameter")
) {
    override fun isApplicableTo(element: KtNameReferenceExpression) = isAutoCreatedItUsage(element)

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val paramToRename = convertImplicitItToExplicit(element, editor) ?: return

        editor.caretModel.moveToOffset(element.textOffset)
        KotlinVariableInplaceRenameHandler().doRename(paramToRename, editor, null)
    }
}
