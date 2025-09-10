// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

class ConvertUnsafeCastCallToUnsafeCastIntention : SelfTargetingIntention<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java,
    KotlinBundle.messagePointer("convert.to.unsafe.cast"),
) {

    override fun isApplicableTo(element: KtDotQualifiedExpression, caretOffset: Int): Boolean {
        if (!element.platform.isJs()) return false
        if ((element.selectorExpression as? KtCallExpression)?.calleeExpression?.text != "unsafeCast") return false

        val fqName = element.resolveToCall()?.resultingDescriptor?.fqNameOrNull()?.asString() ?: return false
        if (fqName != "kotlin.js.unsafeCast") return false

        val type = element.callExpression?.typeArguments?.singleOrNull() ?: return false

        setTextGetter(KotlinBundle.messagePointer("convert.to.0.as.1", element.receiverExpression.text, type.text))
        return true
    }

    override fun applyTo(element: KtDotQualifiedExpression, editor: Editor?) {
        val type = element.callExpression?.typeArguments?.singleOrNull() ?: return
        val newExpression = KtPsiFactory(element.project).createExpressionByPattern("$0 as $1", element.receiverExpression, type.text)
        element.replace(newExpression)
    }

}