// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.KtBinaryExpression

class ConvertToRawStringTemplateIntention : ConvertToStringTemplateIntention() {
    init {
        text = KotlinBundle.message("convert.concatenation.to.raw.string")
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val replaced = element.replaced(buildReplacement(element))
        ToRawStringLiteralIntention().applyTo(replaced, editor)
    }
}