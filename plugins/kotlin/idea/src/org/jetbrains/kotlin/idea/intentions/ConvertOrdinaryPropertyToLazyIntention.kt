// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertOrdinaryPropertyToLazyIntention : SelfTargetingIntention<KtProperty>(
    KtProperty::class.java, KotlinBundle.lazyMessage("convert.to.lazy.property")
) {
    override fun isApplicableTo(element: KtProperty, caretOffset: Int): Boolean =
        !element.isVar && element.initializer != null && element.getter == null && !element.isLocal &&
                !element.hasModifier(KtTokens.CONST_KEYWORD)

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val initializer = element.initializer ?: return
        val psiFactory = KtPsiFactory(element.project)
        val newExpression = if (initializer is KtCallExpression && initializer.isCalling(FqName("kotlin.run"))) {
            initializer.calleeExpression?.replace(psiFactory.createExpression("lazy"))
            initializer
        } else {
            psiFactory.createExpressionByPattern("lazy { $0 }", initializer)
        }
        element.addAfter(psiFactory.createPropertyDelegate(newExpression), initializer)
        element.initializer = null
    }
}
