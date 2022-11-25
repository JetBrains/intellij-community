// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class EnumValuesSoftDeprecateInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isEnumValuesSoftDeprecateEnabled()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                if ("values" != expression.methodExpression.referenceName ||
                    !expression.argumentList.isEmpty) {
                    return
                }
                val resolvedMethod = expression.resolveMethod()
                if ((resolvedMethod as? KtLightMethod)?.containingClass?.isEnum == true) {
                    holder.registerProblem(
                        expression,
                        KotlinBundle.message("inspection.enum.values.method.soft.deprecate.in.java.display.name"),
                    )
                }
            }
        }
    }
}
