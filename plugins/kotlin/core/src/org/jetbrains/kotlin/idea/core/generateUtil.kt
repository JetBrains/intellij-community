// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.utils.IDEAPlatforms
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

@IDEAPluginsCompatibilityAPI(
    IDEAPlatforms._213,
    message = "Use 'insertMembersAfterAndReformat()' instead",
    plugins = "Android IDEA plugin: AndroidCreateOnClickHandlerAction.java"
)
@Deprecated(
    "Use 'insertMembersAfterAndReformat()' instead",
    ReplaceWith("insertMembersAfterAndReformat(editor, classOrObject, declaration, anchor)")
)
fun <T : KtDeclaration> insertMember(editor: Editor?, classOrObject: KtClassOrObject, declaration: T, anchor: PsiElement? = null): T {
    return insertMembersAfterAndReformat(editor, classOrObject, declaration, anchor)
}

@Deprecated(
    "Use 'insertMembersAfter()' instead",
    ReplaceWith("insertMembersAfter(editor, classOrObject, members, anchor, getAnchor)")
)
@JvmName("insertMembersAfter")
@Suppress("unused")
fun <T : KtDeclaration> insertMembersAfterOld(
    editor: Editor?,
    classOrObject: KtClassOrObject,
    members: Collection<T>,
    anchor: PsiElement? = null,
    getAnchor: (KtDeclaration) -> PsiElement? = { null },
): List<SmartPsiElementPointer<T>> {
    return insertMembersAfter(editor, classOrObject, members, anchor, getAnchor)
}