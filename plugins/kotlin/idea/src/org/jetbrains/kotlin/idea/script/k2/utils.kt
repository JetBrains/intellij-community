// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.kotlin.lexer.KtTokens.SHEBANG_COMMENT

private const val MAIN_KTS = "main.kts"

fun isMainKtsScript(virtualFile: VirtualFile) = virtualFile.name == MAIN_KTS || virtualFile.name.endsWith(".$MAIN_KTS")

fun PsiFile.hasShebangComment(): Boolean =
    SyntaxTraverser.psiTraverser(/* root = */ this)
        .withTraversal(TreeTraversal.LEAVES_DFS)
        .traverse()
        .filterIsInstance<PsiComment>()
        .any { element: PsiElement -> element.isShebangComment() }

private fun PsiElement.isShebangComment(): Boolean =
    this is PsiComment && tokenType === SHEBANG_COMMENT