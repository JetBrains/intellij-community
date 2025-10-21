// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertStringTemplateToBuildStringCall
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

class ConvertStringTemplateToBuildStringIntention : SelfTargetingIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.messagePointer("convert.string.template.to.build.string"),
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression, caretOffset: Int): Boolean {
        return element.isSingleQuoted() && !element.isInsideAnnotationEntryArgumentList() && element.interpolationPrefix == null
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val buildStringCall = convertStringTemplateToBuildStringCall(element)
        ShortenReferences.DEFAULT.process(buildStringCall)
    }
}