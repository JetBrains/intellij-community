// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeUtils.removeTypeReference
import org.jetbrains.kotlin.idea.codeinsight.utils.getClassId
import org.jetbrains.kotlin.psi.*

class RedundantExplicitTypeInspection : AbstractKotlinInspection() {

    private fun KaSession.isCompanionObject(type: KaType): Boolean {
        val symbol = type.symbol as? KaClassSymbol ?: return false
        return symbol.classKind == KaClassKind.COMPANION_OBJECT
    }


    private fun KaSession.hasRedundantType(property: KtProperty): Boolean {
        if (!property.isLocal) return false
        val typeReference = property.typeReference ?: return false
        if (typeReference.annotationEntries.isNotEmpty()) return false
        val initializer = property.initializer ?: return false

        val type = property.returnType
        if (type.abbreviation != null) return false


        when (initializer) {
            is KtConstantExpression -> {
                val fqName = initializer.getClassId()?.asSingleFqName() ?: return false
                val classType = type as? KaClassType ?: return false
                val typeFqName = classType.symbol.classId?.asSingleFqName() ?: return false
                if (typeFqName != fqName || type.isMarkedNullable) return false
            }

            is KtStringTemplateExpression -> {
                if (!type.isStringType || type.isMarkedNullable) return false
            }

            is KtNameReferenceExpression -> {
                if (typeReference.text != initializer.getReferencedName()) return false
                val initializerType = initializer.expressionType ?: return false
                if (!initializerType.semanticallyEquals(type) && isCompanionObject(initializerType)) return false
            }

            is KtCallExpression -> {
                if (typeReference.text != initializer.calleeExpression?.text) return false
            }

            else -> return false
        }
        return true
    }

    private class RemoveRedundantTypeFix(element: KtProperty) : AbstractKotlinApplicableQuickFix<KtProperty>(element) {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("remove.explicit.type.specification")

        override fun apply(
            element: KtProperty,
            project: Project,
            editor: Editor?,
            file: KtFile
        ) {
            element.removeTypeReference()
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        propertyVisitor(fun(property) {
            val typeReference = property.typeReference ?: return
            analyze(property) {
                if (hasRedundantType(property)) {
                    holder.registerProblem(
                        typeReference,
                        KotlinBundle.message("explicitly.given.type.is.redundant.here"),
                        IntentionWrapper(RemoveRedundantTypeFix(property).asIntention())
                    )
                }
            }
        })
}