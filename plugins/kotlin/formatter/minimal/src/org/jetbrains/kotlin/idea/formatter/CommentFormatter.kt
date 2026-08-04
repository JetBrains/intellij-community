// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.psi.PsiComment
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A formatter used for formatting KDoc comments.
 * Currently, it strips trailing whitespace from every line of the comment.
 * This formatter is mostly ported from the Java equivalent `com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter`.
 */
internal class CommentFormatter {
    companion object {
        private val LOG: Logger = Logger.getInstance(CommentFormatter::class.java)
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

        val newCommentText = stripSpaces(oldText)

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
