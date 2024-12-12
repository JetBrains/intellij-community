// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.primaryConstructorVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.types.Variance

internal class SelfReferenceConstructorParameterInspection :
    KotlinApplicableInspectionBase.Simple<KtPrimaryConstructor, SelfReferenceConstructorParameterInspection.Context>() {

    data class Context(
        val parameterIndex: Int,
        val nullableType: String,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = primaryConstructorVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtPrimaryConstructor,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("constructor.has.non.null.self.reference.parameter")

    context(KaSession@KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun prepareContext(element: KtPrimaryConstructor): Context? {
        val parameterList = element.valueParameterList ?: return null
        val containingClass = parameterList.containingClass() ?: return null
        val className = containingClass.name ?: return null
        val parameter = parameterList.parameters.firstOrNull { it.typeReference?.text == className } ?: return null
        if (parameter.isVarArg) return null

        val typeReference = parameter.typeReference ?: return null
        val type = typeReference.type
        if (type.isMarkedNullable) return null
        if (type.expandedSymbol != containingClass.symbol) return null

        return Context(
            parameter.parameterIndex(),
            type.withNullability(KaTypeNullability.NULLABLE).render(position = Variance.INVARIANT)
        )
    }

    override fun createQuickFix(
        element: KtPrimaryConstructor,
        context: Context,
    ): KotlinModCommandQuickFix<KtPrimaryConstructor> = object : KotlinModCommandQuickFix<KtPrimaryConstructor>() {

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.nullable.type.fix.text")

        override fun applyFix(
            project: Project,
            element: KtPrimaryConstructor,
            updater: ModPsiUpdater
        ) {

            val parameter = element.valueParameterList?.parameters[context.parameterIndex] ?: return
            parameter.typeReference = KtPsiFactory(project).createType(context.nullableType)
        }
    }
}
