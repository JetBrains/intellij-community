// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.handlers.fixers


import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

class KtClassBodyFixer : KotlinClassBodyFixer() {
    override fun fixSuperTypeInitializer(
        psiElement: KtClassOrObject,
        editor: Editor,
        endOffset: Int
    ): Int {
        val notInitializedSuperType = psiElement.superTypeListEntries.firstOrNull {
            if (it is KtSuperTypeCallEntry) return@firstOrNull false
            val resolved = it.typeAsUserType?.referenceExpression?.mainReference?.resolve()
            (resolved as? KtClass)?.isInterface() == false || (resolved as? PsiClass)?.isInterface == false
        }
        if (notInitializedSuperType != null) {
            editor.document.insertString(notInitializedSuperType.endOffset, "()")
            return endOffset + 2
        }
        return endOffset
    }
}