// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.copyright

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.containers.TreeTraversal
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens.SHEBANG_COMMENT
import org.jetbrains.kotlin.psi.KtDeclaration

internal class UpdateKotlinCopyright(project: Project?, module: Module?, root: VirtualFile?, copyrightProfile: CopyrightProfile?) :
    UpdatePsiFileCopyright(project, module, root, copyrightProfile) {

    override fun accept(): Boolean =
        file.fileType === KotlinFileType.INSTANCE

    override fun scanFile() {
        val comments = file.getExistingComments()
        val anchor = if (file.firstChild?.isShebangComment() == true) file.firstChild?.nextSibling else comments.lastOrNull()
        checkComments(/* last = */ anchor, /* commentHere = */ true, comments)
    }
}

private fun PsiFile.getExistingComments(): List<PsiComment> =
    SyntaxTraverser.psiTraverser(/* root = */ this)
        .withTraversal(TreeTraversal.LEAVES_DFS)
        .traverse()
        .skipWhile { element: PsiElement -> element.isShebangComment() }
        .takeWhile { element: PsiElement ->
            (element is PsiComment && element.getParent() !is KtDeclaration) ||
                    element is PsiWhiteSpace ||
                    element.text.isEmpty() ||
                    element.parent is KDoc
        }
        .map { element: PsiElement -> if (element.parent is KDoc) element.parent else element }
        .filterIsInstance<PsiComment>()
        .toList()

private fun PsiElement.isShebangComment(): Boolean =
    this is PsiComment && tokenType === SHEBANG_COMMENT
