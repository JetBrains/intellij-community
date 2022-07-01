// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

class ReplaceReadLineWithReadlnInspection : AbstractKotlinInspection() {
    companion object {
        private val readLineFqName = FqName("kotlin.io.readLine")
        private val readlnFqName = FqName("kotlin.io.readln")
        private val readlnOrNullFqName = FqName("kotlin.io.readlnOrNull")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = callExpressionVisitor(fun(callExpression) {
        if (callExpression.languageVersionSettings.apiVersion < ApiVersion.KOTLIN_1_6) return
        if (!callExpression.isCalling(readLineFqName)) return

        val qualifiedOrCall = callExpression.getQualifiedExpressionForSelectorOrThis()
        val parent = qualifiedOrCall.parent
        val (targetExpression, newFunction) = if (parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL) {
            parent to readlnFqName
        } else {
            qualifiedOrCall to readlnOrNullFqName
        }
        val newFunctionName = newFunction.shortName().asString()

        holder.registerProblem(
            targetExpression,
            KotlinBundle.message("replace.0.with.1", readLineFqName.shortName().asString(), newFunctionName),
            ReplaceFix(newFunctionName)
        )
    })

    private class ReplaceFix(private val functionName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", functionName)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            descriptor.psiElement.replace(KtPsiFactory(project).createExpression("$functionName()"))
        }
    }
}
