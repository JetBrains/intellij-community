// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.psi.KtDeclaration

class UpdateKotlinCopyright(
    project: Project?,
    module: Module?,
    root: VirtualFile?,
    copyrightProfile: CopyrightProfile?
) : UpdatePsiFileCopyright(project, module, root, copyrightProfile) {

    override fun accept(): Boolean =
        file.fileType === KotlinFileType.INSTANCE

    override fun scanFile() {
        val comments = getExistentComments(file)
        checkComments(comments.lastOrNull(), true, comments)
    }

    companion object {
        fun getExistentComments(psiFile: PsiFile): List<PsiComment> =
            SyntaxTraverser.psiTraverser(psiFile)
                .withTraversal(TreeTraversal.LEAVES_DFS)
                .traverse()
                .takeWhile { element: PsiElement ->
                    (element is PsiComment && element.getParent() !is KtDeclaration) ||
                            element is PsiWhiteSpace ||
                            element.text.isEmpty() ||
                            element.parent is KDoc
                }
                .map { element: PsiElement -> if (element.parent is KDoc) element.parent else element }
                .filterIsInstance<PsiComment>()
                .toList()
    }
}
