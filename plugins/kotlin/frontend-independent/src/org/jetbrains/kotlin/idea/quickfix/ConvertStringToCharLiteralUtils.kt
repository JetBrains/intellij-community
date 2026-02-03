// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression


object ConvertStringToCharLiteralUtils {

    fun prepareCharLiteral(element: KtStringTemplateExpression): KtConstantExpression? {
        val stringTemplateEntry = element.applicableEntry ?: return null
        return stringTemplateEntry.charLiteral()
    }
}

private fun KtStringTemplateEntry.charLiteral(): KtConstantExpression? {
    val text = text.replace("'", "\\'").replace("\\\"", "\"")
    return KtPsiFactory(project).createExpression("'$text'") as? KtConstantExpression
}

private val KtStringTemplateExpression.applicableEntry: KtStringTemplateEntry?
    get() = entries.singleOrNull().takeIf { it is KtLiteralStringTemplateEntry || it is KtEscapeStringTemplateEntry }
