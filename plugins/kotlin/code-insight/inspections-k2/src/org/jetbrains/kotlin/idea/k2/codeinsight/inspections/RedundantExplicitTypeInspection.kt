// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.removeDeclarationTypeReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.inferClassIdByPsi

internal class RedundantExplicitTypeInspection : KotlinApplicableInspectionBase.Simple<KtProperty, Unit>() {

    private fun isCompanionObject(type: KaType): Boolean {
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
                val fqName = initializer.inferClassIdByPsi()?.asSingleFqName() ?: return false
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

    private class RemoveRedundantTypeFix : KotlinModCommandQuickFix<KtProperty>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("remove.explicit.type.specification")

        override fun applyFix(
            project: Project,
            element: KtProperty,
            updater: ModPsiUpdater
        ) {
            element.removeDeclarationTypeReference()
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtProperty): List<TextRange> = ApplicabilityRange.single(element) { it.typeReference }

    override fun getProblemDescription(element: KtProperty, context: Unit): String =
        KotlinBundle.message("explicitly.given.type.is.redundant.here")

    override fun createQuickFix(
        element: KtProperty,
        context: Unit
    ): KotlinModCommandQuickFix<KtProperty> = RemoveRedundantTypeFix()

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        return element.typeReference != null
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        return hasRedundantType(element).asUnit
    }
}