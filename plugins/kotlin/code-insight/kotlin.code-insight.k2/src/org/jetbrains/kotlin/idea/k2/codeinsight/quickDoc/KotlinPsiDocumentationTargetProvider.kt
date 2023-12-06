// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val elementWithDocumentation = element.navigationElement ?: element
        return if (elementWithDocumentation.language.`is`(KotlinLanguage.INSTANCE)) KotlinDocumentationTarget(elementWithDocumentation, originalElement) else null
    }
}

class KotlinDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val element = file.findElementAt(offset) ?: return emptyList()
        return if (element.isModifier()) {
            arrayListOf(KotlinDocumentationTarget(element, element))
        } else emptyList()
    }
}