// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_6
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

private val readLineFqName = FqName("kotlin.io.readLine")

class ReplaceReadLineWithReadlnInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = callExpressionVisitor(fun(callExpression) {
        if (callExpression.languageVersionSettings.apiVersion < KOTLIN_1_6) return
        if (!callExpression.isCalling(readLineFqName)) return

        val qualifiedOrCall = callExpression.getQualifiedExpressionForSelectorOrThis()
        val parent = qualifiedOrCall.parent
        val (targetExpression, newFunctionName) = if (parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL) {
            parent to "readln"
        } else {
            qualifiedOrCall to "readlnOrNull"
        }
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
            val replaced = descriptor.psiElement.replace(KtPsiFactory(project).createExpression("kotlin.io.$functionName()"))
            ShortenReferences.DEFAULT.process(replaced as KtElement)
        }
    }
}
