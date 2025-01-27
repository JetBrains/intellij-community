// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.utils.checkMayBeConstantByFields
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MayBeConstantInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MayBeConstantInspectionBase.Status.JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MayBeConstantInspectionBase.Status.NONE
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.matchStatus
import org.jetbrains.kotlin.idea.inspections.MayBeConstantInspection.Util.getStatus
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierFix
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.ErrorValue
import org.jetbrains.kotlin.resolve.constants.NullValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.constants.evaluate.isStandaloneOnlyConstant
import org.jetbrains.kotlin.resolve.jvm.annotations.hasJvmFieldAnnotation
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode


class MayBeConstantInspection : MayBeConstantInspectionBase() {
    override fun createAddConstModifierFix(property: KtProperty): AddModifierFix {
        return AddConstModifierFix(property)
    }

    override fun KtProperty.getConstantStatus(): Status {
        return getStatus()
    }

    object Util {
        fun KtProperty.getStatus(): Status {
            if (!checkMayBeConstantByFields()) return NONE

            val initializer = initializer // For some reason constant evaluation does not work for property.analyze()
            val context = (initializer ?: this).safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
            val propertyDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, this] as? VariableDescriptor ?: return NONE
            val type = propertyDescriptor.type
            if (!KotlinBuiltIns.isPrimitiveType(type) && !KotlinBuiltIns.isString(type)) return NONE

            val withJvmField = propertyDescriptor.hasJvmFieldAnnotation()
            if (annotationEntries.isNotEmpty() && !withJvmField) return NONE

            return when {
                initializer != null -> {
                    val compileTimeConstant = ConstantExpressionEvaluator.getConstant(
                        initializer, context
                    ) ?: return NONE
                    val erroneousConstant = compileTimeConstant.usesNonConstValAsConstant
                    compileTimeConstant.toConstantValue(propertyDescriptor.type).takeIf {
                        !it.isStandaloneOnlyConstant() && it !is NullValue && it !is ErrorValue
                    } ?: return NONE
                    matchStatus(withJvmField, erroneousConstant)
                }
                withJvmField -> JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER
                else -> NONE
            }
        }
    }
}