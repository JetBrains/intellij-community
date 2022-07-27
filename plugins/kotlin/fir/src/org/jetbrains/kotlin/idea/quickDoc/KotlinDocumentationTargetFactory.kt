// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickDoc

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.psi.PsiDocumentationTargetFactory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinDocumentationTargetFactory : PsiDocumentationTargetFactory {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        return if (element.language.`is`(KotlinLanguage.INSTANCE)) KotlinDocumentationTarget(element, originalElement) else null
    }
}