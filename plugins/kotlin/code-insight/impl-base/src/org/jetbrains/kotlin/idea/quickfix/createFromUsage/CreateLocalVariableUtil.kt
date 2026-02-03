// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

object CreateLocalVariableUtil {
    fun convert(declaration: KtDeclarationWithBody, withReformat: Boolean = false): KtDeclarationWithBody {
        val context = createContext(declaration, withReformat) ?: return declaration
        ConvertToBlockBodyUtils.convert(declaration, context)
        return declaration
    }

    private fun createContext(declaration: KtDeclarationWithBody, reformat: Boolean = false): ConvertToBlockBodyContext? {
        if (!ConvertToBlockBodyUtils.isConvertibleByPsi(declaration)) return null

        declaration.bodyExpression ?: return null

        return ConvertToBlockBodyContext(
            returnTypeIsUnit = false,
            returnTypeIsNothing = false,
            returnTypeString = "kotlin.Any?",
            bodyTypeIsUnit = false,
            bodyTypeIsNothing = false,
            reformat = reformat
        )
    }
}