// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractGoToImplementationCompletionCommandProvider
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal class KotlinGoToImplementationCommandCompletionProvider : AbstractGoToImplementationCompletionCommandProvider() {
    override fun canGoToImplementation(element: PsiElement, offset: Int): Boolean {
        if (element.tokenType != KtTokens.IDENTIFIER) return false
        val member = element.parentOfType<KtNamedDeclaration>()
        val name = member?.nameIdentifier ?: return false
        val containingClass = member as? KtClass ?: member.containingClass() ?: return false
        val lightClass = containingClass.toLightClass() ?: containingClass.toFakeLightClass()
        if (ClassInheritorsSearch.search(lightClass, false)
                .findFirst() == null && !(LambdaUtil.isFunctionalClass(lightClass) && ReferencesSearch.search(lightClass)
                .findFirst() != null)
        ) {
            return false
        }
        val fileDocument = element.containingFile.fileDocument
        return fileDocument.getLineNumber(name.textRange.startOffset) == fileDocument.getLineNumber(offset)
    }
}
