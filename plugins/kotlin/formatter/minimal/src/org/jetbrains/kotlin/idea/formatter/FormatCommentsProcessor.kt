// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.lang.ASTNode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.psi.KtFile

/**
 * This pre-processor is responsible for formatting KDoc comments and stripping trailing whitespaces.
 * This class is mostly ported from the Java equivalent `com.intellij.psi.impl.source.codeStyle.FormatCommentsProcessor`.
 * See [CommentFormatter] for more information about capabilities and options.
 */
internal class FormatCommentsProcessor : PreFormatProcessor {

    override fun process(
        element: ASTNode,
        range: TextRange
    ): TextRange {
        val psiElement = SourceTreeToPsiMap.treeElementToPsi(element)
        if (psiElement?.language != KotlinLanguage.INSTANCE) return range
        val project = psiElement.project
        val containingFile = psiElement.containingFile

        val documentSettings = CodeStyleManager.getInstance(project).getDocCommentSettings(containingFile)
        if (!documentSettings.isDocFormattingEnabled) return range
        if (InjectedLanguageManager.getInstance(project).isInjectedFragment(containingFile)) {
            return range
        }

        val ktFile = containingFile as? KtFile ?: return range
        return formatCommentsInner(element, range, CommentFormatter(ktFile))
    }

    /**
     * Walks the [root] looking for KDoc nodes in the [range] and formats them using the [formatter].
     */
    private fun formatCommentsInner(root: ASTNode, range: TextRange, formatter: CommentFormatter): TextRange {
        var resultTextRange = range
        val pending = ArrayDeque<ASTNode>()
        pending.addLast(root)

        while (pending.isNotEmpty()) {
            val node = pending.removeLast()

            // The node starts past the range, so neither it nor its children can be in it.
            if (resultTextRange.endOffset < node.startOffset) continue

            if (node.elementType == KDocTokens.KDOC) {
                if (!resultTextRange.contains(node.textRange)) continue

                // The KDoc node itself is replaced, so the length change has to be measured on its parent.
                val anchor = node.treeParent ?: continue
                val lengthBefore = anchor.textRange.length
                formatter.processComment(node)
                val delta = anchor.textRange.length - lengthBefore
                resultTextRange = TextRange(resultTextRange.startOffset, resultTextRange.endOffset + delta)
                // A KDoc cannot contain another KDoc, so there is nothing below it to visit.
                continue
            }

            // Children are pushed in reverse so that they are visited in document order.
            var child = node.lastChildNode
            while (child != null) {
                pending.addLast(child)
                child = child.treePrev
            }
        }

        return resultTextRange
    }
}
