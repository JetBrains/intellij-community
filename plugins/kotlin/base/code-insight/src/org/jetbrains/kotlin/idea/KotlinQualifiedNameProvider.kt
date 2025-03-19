// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.ide.actions.JavaQualifiedNameProvider
import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

class KotlinQualifiedNameProvider : QualifiedNameProvider {
    override fun adjustElementToCopy(element: PsiElement) = null

    override fun getQualifiedName(element: PsiElement) = when (element) {
        is KtClassOrObject -> element.fqName?.asString()
        is KtFunction -> getJavaQualifiedName(LightClassUtil.getLightClassMethod(element))

        is KtProperty -> {
            val lightClassPropertyMethods = LightClassUtil.getLightClassPropertyMethods(element)
            val lightElement: PsiElement? = lightClassPropertyMethods.getter ?: lightClassPropertyMethods.backingField
            getJavaQualifiedName(lightElement)
        }
        is KtNameReferenceExpression -> element.mainReference.resolve()?.let {
            QualifiedNameProviderUtil.getQualifiedName(it)
        }
        else -> null
    }

    private fun getJavaQualifiedName(element: PsiElement?) = element?.let { JavaQualifiedNameProvider().getQualifiedName(element) }

    override fun qualifiedNameToElement(fqn: String, project: Project) = null

    override fun insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project) {
    }
}
