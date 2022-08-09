// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.allChildren


/**
 * This function is intended for debug purposes only. It complements "View PSI Structure" allowing to see PSI tree for arbitrary
 * [PsiElement] in the runtime. Tree has nice and handy format to track its structure. The element itself is marked with '(*)' symbol.
 * @param withMeAsRoot if 'true' the output contains only a subtree with 'this' as a root; otherwise entire tree is provided
 * @param indentStep specifies indentation of child nodes relative the their parens.
 */
fun PsiElement.printTree(withMeAsRoot: Boolean = false, indentStep: Int = 3): String {

    fun PsiElement.printTreeInternal(
        indent: Int = 0,
        result: StringBuilder = StringBuilder(),
        toMark: PsiElement? = this,
        entire: Boolean = true,
        indentStep: Int
    ): String {
        if (entire) return containingFile.printTreeInternal(toMark = toMark, entire = false, indentStep = indentStep)

        val indentSymbols = if (indent > 0) ".".repeat(indent) else ""
        result.append(javaClass.simpleName.prependIndent(indentSymbols))
            .append(" [").append(text).append("]")
            .append(if (this == toMark) " (*)" else "")

        val nextIndent = indent + indentStep
        this.allChildren.forEach {
            result.append("\n")
            it.printTreeInternal(nextIndent, result, toMark, entire = false, indentStep = indentStep)
        }

        return result.toString()
    }

    return printTreeInternal(entire = !withMeAsRoot, indentStep = indentStep)
}