// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.declarations

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.splitPropertyDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class SplitPropertyDeclarationIntention : SelfTargetingRangeIntention<KtProperty>(
    KtProperty::class.java,
    KotlinBundle.messagePointer("split.property.declaration")
), LowPriorityAction {
    override fun applicabilityRange(element: KtProperty): TextRange? {
        if (!element.isLocal || element.parent is KtWhenExpression) return null
        val initializer = element.initializer ?: return null
        return TextRange(element.startOffset, initializer.startOffset)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        splitPropertyDeclaration(element)
    }
}
