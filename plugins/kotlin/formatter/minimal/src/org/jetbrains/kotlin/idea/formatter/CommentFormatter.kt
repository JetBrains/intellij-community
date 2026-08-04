// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A formatter used for formatting KDoc comments and stripping trailing whitespace.
 * This formatter is mostly ported from the Java equivalent `com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter`.
 * See [KDocParser] for more information about capabilities and options.
 */
internal class CommentFormatter(
    ktFile: KtFile,
) {
    companion object {
        private val LOG: Logger = Logger.getInstance(CommentFormatter::class.java)
    }

    private val codeStyleSettings: CodeStyleSettings = CodeStyle.getSettings(ktFile)
    private val parser = KDocParser(codeStyleSettings.kotlinCommonSettings)

    /**
     * Returns the indent of the line this element starts on, in columns.
     */
    private fun PsiElement.getIndentColumn(): Int {
        // Get the whitespace before the element
        val indent = (PsiTreeUtil.prevLeaf(this) as? PsiWhiteSpace)?.text ?: return 0
        val lineBreak = indent.lastIndexOf('\n')
        // The element does not start its own line, so there is nothing to align the continuation lines to.
        if (lineBreak < 0) return 0

        val tabSize = codeStyleSettings.getTabSize(KotlinFileType.INSTANCE)
        var column = 0
        for (i in lineBreak + 1 until indent.length) {
            column += if (indent[i] == '\t') tabSize - column % tabSize else 1
        }
        return column
    }

    private fun stripSpaces(text: String): String {
        val lines = LineTokenizer.tokenize(text.toCharArray(), false)
        return lines.joinToString("\n") { it.trimEnd() }
    }

    internal fun processComment(element: ASTNode?) {
        val psiElement = SourceTreeToPsiMap.treeElementToPsi(element)
        if (psiElement?.language != KotlinLanguage.INSTANCE) return
        val comment = psiElement as? PsiComment ?: (psiElement as? KtDeclaration)?.docComment
        val oldComment = comment as? KDoc ?: return
        val oldText = oldComment.text

        val indent = psiElement.getIndentColumn()

        // Note that stripping of spaces at the end should occur even if wrapping of comments is disabled
        var newCommentText = parser.wrapComment(oldText, " ".repeat(indent)) ?: oldText
        newCommentText = stripSpaces(newCommentText)

        // nothing has changed
        if (newCommentText == oldText) return

        try {
            val newComment = createKDocFromText(psiElement.project, newCommentText) ?: return
            val oldNode = oldComment.node
            val newNode = newComment.node
            val parent = oldNode.treeParent
            // important to replace with tree operation to avoid resolve and repository update
            parent.replaceChild(oldNode, newNode)
        } catch (e: IncorrectOperationException) {
            LOG.error(e)
        }
    }

    /**
     * Parses [text] into a KDoc.
     * Ported from `org.jetbrains.kotlin.idea.kdoc.KDocElementFactory` but it cannot be used here due
     * to dependency issues.
     */
    private fun createKDocFromText(project: Project, text: String): KDoc? {
        val declaration = KtPsiFactory(project).createDeclaration<KtFunction>("$text fun foo { }")
        return PsiTreeUtil.findChildOfType(declaration, KDoc::class.java)
    }
}
