// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSemicolon
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.DeletePsiElementOfInterestFix
import org.jetbrains.kotlin.lexer.KtTokens

class RedundantSemicolonInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element.node.elementType == KtTokens.SEMICOLON && isRedundantSemicolon(element)) {
                    holder.registerProblem(
                        element,
                        KotlinBundle.message("redundant.semicolon"),
                        DeletePsiElementOfInterestFix
                    )
                }
            }
        }
    }
}