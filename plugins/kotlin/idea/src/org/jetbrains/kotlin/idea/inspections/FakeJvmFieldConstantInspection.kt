// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.elements.KtLightFieldForSourceDeclarationSupport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.MayBeConstantInspection.Util.getStatus
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MayBeConstantInspectionBase.Status.*
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierFix
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

class FakeJvmFieldConstantInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
                super.visitAnnotationParameterList(list)

                for (attribute in list.attributes) {
                    val valueExpression = attribute.value as? PsiExpression ?: continue
                    checkExpression(valueExpression, holder)
                }
            }

            override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
                super.visitSwitchLabelStatement(statement)

                statement.caseLabelElementList?.elements?.forEach { labelElement ->
                    (labelElement as? PsiExpression)?.let { checkExpression(it, holder) }
                }
            }

            override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                super.visitAssignmentExpression(expression)

                if (expression.operationTokenType != JavaTokenType.EQ) return
                val leftType = expression.lExpression.type as? PsiPrimitiveType ?: return
                checkAssignmentChildren(expression.rExpression ?: return, leftType, holder)
            }

            override fun visitVariable(variable: PsiVariable) {
                super.visitVariable(variable)

                val leftType = variable.type as? PsiPrimitiveType ?: return
                val initializer = variable.initializer ?: return
                checkAssignmentChildren(initializer, leftType, holder)
            }
        }
    }

    private fun checkAssignmentChildren(right: PsiExpression, leftType: PsiPrimitiveType, holder: ProblemsHolder) {
        if (leftType == PsiTypes.booleanType() || leftType == PsiTypes.charType() || leftType == PsiTypes.voidType()) return
        right.forEachDescendantOfType<PsiExpression>(canGoInside = { parentElement ->
            parentElement !is PsiCallExpression && parentElement !is PsiTypeCastExpression
        }) { rightPart ->
            checkExpression(rightPart, holder) { resolvedPropertyType ->
                leftType != resolvedPropertyType && !leftType.isAssignableFrom(resolvedPropertyType)
            }
        }
    }

    private fun checkExpression(
        valueExpression: PsiExpression,
        holder: ProblemsHolder,
        additionalTypeCheck: (PsiType) -> Boolean = { true }
    ) {
        val resolvedLightField = (valueExpression as? PsiReference)?.resolve() as? KtLightFieldForSourceDeclarationSupport
            ?: return
        val resolvedProperty = resolvedLightField.kotlinOrigin as? KtProperty ?: return
        if (resolvedProperty.annotationEntries.isEmpty()) return
        val resolvedPropertyStatus = resolvedProperty.getStatus()
        if (resolvedPropertyStatus == JVM_FIELD_MIGHT_BE_CONST ||
            resolvedPropertyStatus == JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER ||
            resolvedPropertyStatus == JVM_FIELD_MIGHT_BE_CONST_ERRONEOUS
        ) {
            val resolvedPropertyType = resolvedLightField.type
            if (!additionalTypeCheck(resolvedPropertyType)) return
            val fixes = mutableListOf<LocalQuickFix>()
            if (resolvedPropertyStatus == JVM_FIELD_MIGHT_BE_CONST) {
                fixes += IntentionWrapper(AddConstModifierFix(resolvedProperty))
            }
            holder.registerProblem(
                valueExpression,
                KotlinBundle.message("use.of.non.const.kotlin.property.as.java.constant.is.incorrect.will.be.forbidden.in.1.4"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes.toTypedArray()
            )
        }
    }
}