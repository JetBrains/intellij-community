// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.utils.IDEAPlatforms
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

@IDEAPluginsCompatibilityAPI(
    IDEAPlatforms._213,
    message = "Use insertMembersAfterAndReformat instead",
    plugins = "android IDEA plugin: AndroidCreateOnClickHandlerAction.java"
)
fun <T : KtDeclaration> insertMember(editor: Editor?, classOrObject: KtClassOrObject, declaration: T, anchor: PsiElement? = null): T =
    insertMembersAfterAndReformat(editor, classOrObject, declaration, anchor)
