// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.ConvertToStringTemplateIntention.Holder.buildReplacement
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ConvertToRawStringTemplateIntention : ConvertToStringTemplateIntention() {
    init {
        setTextGetter(KotlinBundle.messagePointer("convert.concatenation.to.raw.string"))
    }

    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val intention = ToRawStringLiteralIntention()
        return super.isApplicableTo(element) &&
                element.descendantsOfType<KtStringTemplateExpression>().all { intention.isApplicableTo(it) }
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val replaced = element.replaced(buildReplacement(element))
        ToRawStringLiteralIntention().applyTo(replaced, editor)
    }
}