// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*

abstract class MayBeConstantInspectionBase : AbstractKotlinInspection() {
    enum class Status {
        NONE,
        MIGHT_BE_CONST,
        MIGHT_BE_CONST_ERRONEOUS,
        JVM_FIELD_MIGHT_BE_CONST,
        JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER,
        JVM_FIELD_MIGHT_BE_CONST_ERRONEOUS
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return propertyVisitor { property ->
            when (val status = property.getConstantStatus()) {
                Status.NONE, Status.JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER,
                Status.MIGHT_BE_CONST_ERRONEOUS, Status.JVM_FIELD_MIGHT_BE_CONST_ERRONEOUS -> return@propertyVisitor

                Status.MIGHT_BE_CONST, Status.JVM_FIELD_MIGHT_BE_CONST -> {
                    holder.registerProblem(
                        property.nameIdentifier ?: property,
                        if (status == Status.JVM_FIELD_MIGHT_BE_CONST)
                            KotlinBundle.message("const.might.be.used.instead.of.jvmfield")
                        else
                            KotlinBundle.message("might.be.const"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        IntentionWrapper(createAddConstModifierFix(property))
                    )
                }
            }
        }
    }

    protected abstract fun createAddConstModifierFix(property: KtProperty): IntentionAction

    protected abstract fun KtProperty.getConstantStatus(): Status
}

fun matchStatus(withJvmField: Boolean, erroneousConstant: Boolean): MayBeConstantInspectionBase.Status {
    return when {
        withJvmField ->
            if (erroneousConstant) MayBeConstantInspectionBase.Status.JVM_FIELD_MIGHT_BE_CONST_ERRONEOUS
            else MayBeConstantInspectionBase.Status.JVM_FIELD_MIGHT_BE_CONST
        else ->
            if (erroneousConstant) MayBeConstantInspectionBase.Status.MIGHT_BE_CONST_ERRONEOUS
            else MayBeConstantInspectionBase.Status.MIGHT_BE_CONST
    }
}