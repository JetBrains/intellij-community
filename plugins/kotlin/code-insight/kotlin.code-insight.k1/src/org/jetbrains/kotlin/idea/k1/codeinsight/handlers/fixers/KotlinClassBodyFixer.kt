// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k1.codeinsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinClassBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtClassOrObject) return

        val body = psiElement.body
        if (!body?.text.isNullOrBlank()) return

        var endOffset = psiElement.range.end

        if (body != null) {
            body.getPrevSiblingIgnoringWhitespaceAndComments()?.let {
                endOffset = it.endOffset
            }
        }

        val notInitializedSuperType = allowResolveInDispatchThread {
            psiElement.superTypeListEntries.firstOrNull {
                if (it is KtSuperTypeCallEntry) return@firstOrNull false
                val resolved = it.typeAsUserType?.referenceExpression?.mainReference?.resolve()
                resolved.safeAs<KtClass>()?.isInterface() == false || resolved.safeAs<PsiClass>()?.isInterface == false
            }
        }
        if (notInitializedSuperType != null) {
            editor.document.insertString(notInitializedSuperType.endOffset, "()")
            endOffset += 2
        }

        editor.caretModel.moveToOffset(endOffset - 1)

        // Insert '\n' to force a multiline body, otherwise there will be an empty body on one line and a caret on the next one.
        editor.document.insertString(endOffset, "{\n}")
    }
}