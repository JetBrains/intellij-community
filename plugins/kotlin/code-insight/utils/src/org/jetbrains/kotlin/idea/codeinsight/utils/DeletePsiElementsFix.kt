/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class DeletePsiElementsFix(
    @SafeFieldForPreview private val elementsToBeDeleted: List<SmartPsiElementPointer<PsiElement>>,
) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("delete.equals.and.hash.code.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        for (element in elementsToBeDeleted) {
            element.element?.delete()
        }
    }
}