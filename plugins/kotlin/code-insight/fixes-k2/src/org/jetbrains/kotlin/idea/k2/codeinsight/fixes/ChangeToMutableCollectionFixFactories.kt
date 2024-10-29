// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.MutableCollectionsConversionUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.endOffset

internal object ChangeToMutableCollectionFixFactories {

    val noSetMethod = KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoSetMethod> { diagnostic -> createQuickFixes(diagnostic) }

    private fun KaSession.createQuickFixes(diagnostic: KaFirDiagnostic.NoSetMethod): List<ModCommandAction> {
        val element = diagnostic.psi
        val arrayExpr = element.arrayExpression ?: return emptyList()
        val type = arrayExpr.expressionType as? KaClassType ?: return emptyList()
        if (!MutableCollectionsConversionUtils.run { isReadOnlyListOrMap(type) }) return emptyList()

        val property = arrayExpr.mainReference?.resolve() as? KtProperty ?: return emptyList()
        if (!MutableCollectionsConversionUtils.canConvertPropertyType(property)) return emptyList()

        val typeName = type.classId.shortClassName.asString()

        return listOf(ChangeToMutableCollectionFix(property, ElementContext(typeName)))
    }

    private class ElementContext(
        val typeName: String,
    )

    private class ChangeToMutableCollectionFix(property: KtProperty, context: ElementContext) :
        KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, ElementContext>(property, context) {

        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("fix.change.to.mutable.type.family")
        }

        override fun getPresentation(context: ActionContext, element: KtProperty): Presentation {
            val elementContext = getElementContext(context, element)
            return Presentation.of(KotlinBundle.message("fix.change.to.mutable.type.text", "Mutable${elementContext.typeName}"))
        }

        override fun invoke(actionContext: ActionContext, element: KtProperty, elementContext: ElementContext, updater: ModPsiUpdater) {
            val initializer = element.initializer ?: return
            analyze(initializer) {
                val type = initializer.expressionType as? KaClassType ?: return
                MutableCollectionsConversionUtils.run { convertPropertyTypeToMutable(element, type.classId) }
            }
            updater.moveCaretTo(element.endOffset)
        }
    }
}
