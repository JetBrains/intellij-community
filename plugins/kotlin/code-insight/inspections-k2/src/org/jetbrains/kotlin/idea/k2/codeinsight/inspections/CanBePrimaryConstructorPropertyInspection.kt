// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class CanBePrimaryConstructorPropertyInspection :
    KotlinApplicableInspectionBase.Simple<KtProperty, MovePropertyToConstructorInfo>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitProperty(property: KtProperty) {
            visitTargetElement(property, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtProperty, context: MovePropertyToConstructorInfo): String = KotlinBundle.message(
        "property.is.explicitly.assigned.to.parameter.0.can", element.name ?: "???"
    )

    override fun getApplicableRanges(element: KtProperty): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    override fun isApplicableByPsi(element: KtProperty): Boolean = element.isMovableToConstructorByPsi()

    override fun KaSession.prepareContext(element: KtProperty): MovePropertyToConstructorInfo? {
        val initializer = element.initializer ?: return null
        val paramSymbol = initializer.mainReference?.resolveToSymbol() as? KaValueParameterSymbol ?: return null
        if (element.nameAsName != paramSymbol.name) return null
        val propertyType = element.returnType
        val isMergeableType = if (paramSymbol.isVararg) {
            propertyType.arrayElementType?.semanticallyEquals(paramSymbol.returnType) == true
        } else {
            propertyType.semanticallyEquals(paramSymbol.returnType)
        }
        if (!isMergeableType) return null
        return MovePropertyToConstructorInfo.create(element)
    }

    override fun createQuickFix(
        element: KtProperty,
        context: MovePropertyToConstructorInfo,
    ): KotlinModCommandQuickFix<KtProperty> = object : KotlinModCommandQuickFix<KtProperty>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.can.be.primary.constructor.property.display.name")

        override fun applyFix(
            project: Project,
            element: KtProperty,
            updater: ModPsiUpdater,
        ) {
            element.moveToConstructor(context.toWritable(updater))
        }
    }
}