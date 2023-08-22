// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiInlineDocTag
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.psi.KtModifierListOwner

class KotlinInternalInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                expression.checkAndReport(holder)
            }

            override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                reference.checkAndReport(holder)
            }
        }
    }

    private fun PsiElement.checkAndReport(holder: ProblemsHolder) {
        val lightElement = (this as? PsiReference)?.resolve() as? KtLightElement<*, *> ?: return
        val modifierListOwner = lightElement.kotlinOrigin as? KtModifierListOwner ?: return
        if (inSameModule(modifierListOwner)) {
            return
        }

        if (modifierListOwner.hasModifier(INTERNAL_KEYWORD)) {
            // ignore JavaDoc @link references
            if (PsiTreeUtil.getParentOfType(this, PsiInlineDocTag::class.java) != null) return

            holder.registerProblem(this, KotlinBundle.message("usage.of.kotlin.internal.declaration.from.different.module"))
        }
    }

    private fun PsiElement.inSameModule(element: PsiElement) = module?.equals(element.module) ?: true
}
