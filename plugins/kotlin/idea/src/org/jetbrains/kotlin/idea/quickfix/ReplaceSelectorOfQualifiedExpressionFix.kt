// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ReplaceSelectorOfQualifiedExpressionFix(private val newSelector: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.with.0", newSelector)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        (descriptor.psiElement as? KtQualifiedExpression)?.let {
            it.replace(KtPsiFactory(project).createExpressionByPattern("$0.$newSelector", it.receiverExpression))
        }
    }
}
