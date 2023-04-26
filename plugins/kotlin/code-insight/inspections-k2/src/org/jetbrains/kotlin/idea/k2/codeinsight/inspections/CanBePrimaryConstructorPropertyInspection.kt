// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtProperty

internal class CanBePrimaryConstructorPropertyInspection
    : AbstractKotlinApplicableInspectionWithContext<KtProperty, MovePropertyToConstructorInfo>(KtProperty::class) {

    override fun getProblemDescription(element: KtProperty, context: MovePropertyToConstructorInfo): String = KotlinBundle.message(
        "property.is.explicitly.assigned.to.parameter.0.can", element.name ?: "???"
    )

    override fun getActionFamilyName() = KotlinBundle.message("inspection.can.be.primary.constructor.property.display.name")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> = ApplicabilityRanges.DECLARATION_NAME

    override fun isApplicableByPsi(element: KtProperty): Boolean = element.isMovableToConstructorByPsi()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtProperty): MovePropertyToConstructorInfo? {
        val initializer = element.initializer ?: return null
        val paramSymbol = initializer.mainReference?.resolveToSymbol() as? KtValueParameterSymbol ?: return null
        if (element.nameAsName != paramSymbol.name) return null
        return MovePropertyToConstructorInfo.create(element)
    }

    override fun apply(element: KtProperty, context: MovePropertyToConstructorInfo, project: Project, editor: Editor?) {
        element.moveToConstructor(context)
    }
}