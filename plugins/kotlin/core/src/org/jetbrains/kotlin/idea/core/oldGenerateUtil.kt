// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration

fun <T : KtDeclaration> insertMembersAfterAndReformat(
    editor: Editor?,
    classOrObject: KtClassOrObject,
    members: Collection<T>,
    anchor: PsiElement? = null,
    getAnchor: (KtDeclaration) -> PsiElement? = { null },
): List<T> {
    val codeStyleManager = CodeStyleManager.getInstance(classOrObject.project)
    return runWriteAction {
        val insertedMembersElementPointers = insertMembersAfter(editor, classOrObject, members, anchor, getAnchor)
        val firstElement = insertedMembersElementPointers.firstOrNull() ?: return@runWriteAction emptyList()

        fun insertedMembersElements() = insertedMembersElementPointers.mapNotNull { it.element }

        ShortenReferences.DEFAULT.process(insertedMembersElements())
        if (editor != null) {
            firstElement.element?.let { moveCaretIntoGeneratedElement(editor, it) }
        }

        insertedMembersElementPointers.onEach { it.element?.let { element -> codeStyleManager.reformat(element) } }
        insertedMembersElements()
    }
}

fun <T : KtDeclaration> insertMembersAfterAndReformat(editor: Editor?, classOrObject: KtClassOrObject, declaration: T, anchor: PsiElement? = null): T {
    return insertMembersAfterAndReformat(editor, classOrObject, listOf(declaration), anchor).single()
}
