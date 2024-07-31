// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

internal object AddTypeAnnotationToValueParameterFixFactory {

    val addTypeAnnotationToValueParameterFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValueParameterWithoutExplicitType ->
            val element = diagnostic.psi
            val defaultValue = element.defaultValue ?: return@ModCommandBased emptyList()
            val elementContext = getTypeName(element, defaultValue) ?: return@ModCommandBased emptyList()

            listOf(
                AddTypeAnnotationToValueParameterFix(element, elementContext)
            )
        }

    context(KaSession)
    private fun getTypeName(element: KtParameter, defaultValue: KtExpression): String? {
        val type = defaultValue.expressionType ?: return null

        if (type.isArrayOrPrimitiveArray) {
            if (element.hasModifier(KtTokens.VARARG_KEYWORD)) {
                val elementType = type.arrayElementType ?: return null
                return getTypeName(elementType)
            } else if (defaultValue is KtCollectionLiteralExpression) {
                val elementType = type.arrayElementType
                if (elementType?.isPrimitive == true) {
                    val classId = (elementType as KaClassType).classId
                    val arrayTypeName = "${classId.shortClassName}Array"
                    return arrayTypeName
                }
            }
        }
        return getTypeName(type)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getTypeName(type: KaType): String {
        val typeName = type.render(
            KaTypeRendererForSource.WITH_SHORT_NAMES,
            Variance.INVARIANT
        )
        return typeName
    }

    private class AddTypeAnnotationToValueParameterFix(
        element: KtParameter,
        private val typeName: String,
    ) : PsiUpdateModCommandAction<KtParameter>(element) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtParameter,
            updater: ModPsiUpdater,
        ) {
            element.typeReference = KtPsiFactory(actionContext.project).createType(typeName)
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("fix.add.type.annotation.family")

        override fun getPresentation(
            context: ActionContext,
            element: KtParameter,
        ): Presentation {
            val actionName = KotlinBundle.message(
                "fix.add.type.annotation.text",
                typeName,
                element.name.toString(),
            )
            return Presentation.of(actionName)
        }
    }
}
