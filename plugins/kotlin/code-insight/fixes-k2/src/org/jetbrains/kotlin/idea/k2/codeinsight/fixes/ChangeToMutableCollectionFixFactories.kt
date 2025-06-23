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
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.endOffset

object ChangeToMutableCollectionFixFactories {

    val noSetMethod: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoSetMethod> = KotlinQuickFixFactory.ModCommandBased {
        createQuickFixes(diagnostic = it)
    }

    private fun KaSession.createQuickFixes(diagnostic: KaFirDiagnostic.NoSetMethod): List<ModCommandAction> {
        val element = diagnostic.psi
        val arrayExpr = element.arrayExpression ?: return emptyList()
        val type = arrayExpr.expressionType as? KaClassType ?: return emptyList()

        val immutableCollectionClassId = type.classId
        if (!MutableCollectionsConversionUtils.isReadOnlyListOrMap(immutableCollectionClassId)) return emptyList()

        val property = arrayExpr.mainReference?.resolve() as? KtProperty ?: return emptyList()
        if (!MutableCollectionsConversionUtils.canConvertPropertyType(property)) return emptyList()

        return listOf(ChangeToMutableCollectionFix(property, ElementContext(immutableCollectionClassId)))
    }

    data class ElementContext(
        val immutableCollectionClassId: ClassId,
    )

    class ChangeToMutableCollectionFix(
        element: KtDeclarationWithInitializer,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtDeclarationWithInitializer, ElementContext>(element, elementContext) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.change.to.mutable.type.family")

        override fun getPresentation(
            context: ActionContext,
            element: KtDeclarationWithInitializer,
        ): Presentation {
            val className = getElementContext(context, element)
                .immutableCollectionClassId
                .shortClassName
            val name = KotlinBundle.message(
                "fix.change.to.mutable.type.text",
                "Mutable${className.asString()}",
            )
            return Presentation.of(name)
        }

        override fun invoke(
            actionContext: ActionContext,
            element: KtDeclarationWithInitializer,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val initializer = element.initializer ?: return
            analyze(initializer) {
                MutableCollectionsConversionUtils.run {
                    convertPropertyTypeToMutable(
                        property = element,
                        immutableCollectionClassId = elementContext.immutableCollectionClassId,
                    )
                }
            }
            updater.moveCaretTo(element.endOffset)
        }
    }
}
