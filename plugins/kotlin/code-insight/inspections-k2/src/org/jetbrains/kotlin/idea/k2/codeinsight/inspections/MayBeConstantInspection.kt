// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsight.utils.checkMayBeConstantByFields
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceReferencesToGetterByReferenceToField
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MayBeConstantInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.matchStatus
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

class MayBeConstantInspection : MayBeConstantInspectionBase() {
    override fun createAddConstModifierFix(property: KtProperty): AddModifierFix {
        return AddConstModifierFix(property)
    }

    override fun KtProperty.getConstantStatus(): Status {
        if (!checkMayBeConstantByFields()) return Status.NONE

        val initializer = initializer
        analyze(this) {
            if (!hasNonNullablePrimitiveOrStringType()) return Status.NONE
        }

        val withJvmField = findAnnotation(ClassId.fromString(JVM_FIELD_CLASS_ID)) != null
        if (annotationEntries.isNotEmpty() && !withJvmField) return Status.NONE

        return when {
            initializer != null -> {
                analyze(initializer) {
                    val constant = initializer.getConstantValue() ?: return Status.NONE
                    val erroneousConstant = initializer.usesNonConstValAsConstant()

                    if (constant is KaConstantValue.NullValue || constant is KaConstantValue.ErrorValue) return Status.NONE
                    matchStatus(withJvmField, erroneousConstant)
                }
            }
            withJvmField -> Status.JVM_FIELD_MIGHT_BE_CONST_NO_INITIALIZER
            else -> Status.NONE
        }
    }

    context(KaSession)
    private fun KtProperty.hasNonNullablePrimitiveOrStringType(): Boolean {
        val type = this.returnType
        if (type.isMarkedNullable) return false
        return type.isPrimitive || type.isStringType
    }

    context(KaSession)
    private fun KtExpression.getConstantValue(): KaConstantValue? {
        return evaluate()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KtExpression.usesNonConstValAsConstant(): Boolean {
        val diagnostics = diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        return diagnostics.find { it is KaFirDiagnostic.NonConstValUsedInConstantExpression } != null
    }

    private class AddConstModifierFix(property: KtProperty) : AddModifierFix(property, KtTokens.CONST_KEYWORD), CleanupFix {
        override fun startInWriteAction(): Boolean = false

        override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
            val property = element as? KtProperty ?: return
            addConstModifier(property)
        }

        private fun addConstModifier(property: KtProperty) {
            val annotationsToRemove = property.getJvmAnnotations()
            replaceReferencesToGetterByReferenceToField(property, JavaFileType.INSTANCE)
            runWriteActionIfPhysical(property) {
                property.addModifier(KtTokens.CONST_KEYWORD)
                annotationsToRemove.forEach(KtAnnotationEntry::delete)
            }
        }
    }
}