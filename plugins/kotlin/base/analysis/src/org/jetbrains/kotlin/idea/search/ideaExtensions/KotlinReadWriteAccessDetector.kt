// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.codeInsight.highlighting.JavaReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

class KotlinReadWriteAccessDetector : ReadWriteAccessDetector() {
    companion object {
        val INSTANCE: KotlinReadWriteAccessDetector = KotlinReadWriteAccessDetector()
    }

    private val javaReadWriteAccessDetector: JavaReadWriteAccessDetector by lazy { EP_NAME.extensionList.filterIsInstance<JavaReadWriteAccessDetector>().first() }

    override fun isReadWriteAccessible(element: PsiElement): Boolean = element is KtVariableDeclaration || element is KtParameter || javaReadWriteAccessDetector.isReadWriteAccessible(element)

    override fun isDeclarationWriteAccess(element: PsiElement): Boolean = element is KtVariableDeclaration || element is KtParameter || javaReadWriteAccessDetector.isDeclarationWriteAccess(element)

    override fun getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access {
        if (!isReadWriteAccessible(referencedElement)) {
            return Access.Read
        }

        val refTarget = reference.resolve()
        if (refTarget is KtLightMethod) {
            val declaration = when (val origin = refTarget.kotlinOrigin) {
                is KtPropertyAccessor -> origin.getNonStrictParentOfType<KtProperty>()
                is KtProperty, is KtParameter -> origin as KtNamedDeclaration
                else -> null
            } ?: return Access.ReadWrite

            return when (refTarget.name) {
                JvmAbi.getterName(declaration.name!!) -> return Access.Read
                JvmAbi.setterName(declaration.name!!) -> return Access.Write
                else -> Access.ReadWrite
            }
        }

        return getExpressionAccess(reference.element)
    }

    override fun getExpressionAccess(expression: PsiElement): Access {
        if (expression !is KtExpression) { //TODO: there should be a more correct scheme of access type detection for cross-language references
            return javaReadWriteAccessDetector.getExpressionAccess(expression)
        }

        return when (expression.readWriteAccess(useResolveForReadWrite = true)) {
            ReferenceAccess.READ -> Access.Read
            ReferenceAccess.WRITE -> Access.Write
            ReferenceAccess.READ_WRITE -> Access.ReadWrite
        }
    }
}
