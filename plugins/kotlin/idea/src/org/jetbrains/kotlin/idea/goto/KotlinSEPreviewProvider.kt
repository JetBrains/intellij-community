// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getAsJavaPsiElement
import org.jetbrains.uast.toUElement

class KotlinSEPreviewProvider : SearchEverywherePreviewProvider {
    override fun getElement(project: Project, psiFile: PsiFile): PsiElement? {
        val uFile = psiFile.toUElement() as? UFile
        return uFile?.classes?.firstOrNull().getAsJavaPsiElement(PsiClass::class.java)
    }
}