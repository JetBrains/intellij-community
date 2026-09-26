// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

abstract class CalleeReferenceVisitorBase protected constructor(private val deepTraversal: Boolean) : KtTreeVisitorVoid() {

    protected abstract fun processDeclaration(reference: KtSimpleNameExpression, declaration: PsiElement)

    override fun visitKtElement(element: KtElement) {
        if (deepTraversal || !(element is KtClassOrObject || element is KtNamedFunction)) {
            super.visitKtElement(element)
        }
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val declaration = expression.mainReference.resolve()
        if (declaration == null || (declaration.containingFile as? KtFile)?.isCompiled == true) return

        if (declaration is KtProperty && !declaration.isLocal || isCallable(declaration, expression)) {
            processDeclaration(expression, declaration)
        }
    }

    companion object {
        // Accept callees of KtCallElement which refer to Kotlin function, Kotlin class or Java method
        private fun isCallable(declaration: PsiElement, reference: KtSimpleNameExpression): Boolean {
            val callElement = PsiTreeUtil.getParentOfType(reference, KtCallElement::class.java)
            if (callElement == null || !PsiTreeUtil.isAncestor(callElement.calleeExpression, reference, false)) return false

            return declaration is KtClassOrObject
                    || declaration is KtNamedFunction
                    || declaration is PsiMethod
        }
    }
}
