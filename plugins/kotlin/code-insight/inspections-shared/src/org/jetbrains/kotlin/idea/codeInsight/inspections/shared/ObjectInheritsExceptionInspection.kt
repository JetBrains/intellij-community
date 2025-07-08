// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.psi.*

internal class ObjectInheritsExceptionInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
                val isException = analyze(declaration) {
                    val symbol = declaration.symbol as? KaNamedClassSymbol ?: return
                    symbol.superTypes.any {
                        it.isClassType(StandardKotlinNames.exceptionClassId) ||
                                it.isSubtypeOf(StandardKotlinNames.exceptionClassId)
                    }
                }

                if (!isException) return

                holder.registerProblem(
                    declaration.getObjectKeyword() ?: return,
                    KotlinBundle.message("inspection.object.exception.to.class.warning"),
                    ChangeObjectToClassQuickFix()
                )
            }
        }

    private class ChangeObjectToClassQuickFix : PsiUpdateModCommandQuickFix() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("inspection.object.exception.to.class.quick.fix.name")

        override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
            val objectDeclaration =
                (element as? LeafPsiElement)?.let { it.parent as? KtObjectDeclaration } ?: return

            val psiFactory = KtPsiFactory(project)

            val searchParameters = KotlinReferencesSearchParameters(
                objectDeclaration, objectDeclaration.useScope, ignoreAccessScope = false
            )

            val query = ReferencesSearch.search(searchParameters)

            val references = if (IntentionPreviewUtils.isIntentionPreviewActive()) {
                listOfNotNull(query.findFirst())
            } else {
                query.findAll()
            }

            references
                .mapNotNull {
                    if (it !is KtSimpleNameReference) return@mapNotNull null
                    val expression = it.element

                    if (expression.parent is KtUserType ||
                        PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java, false, KtBlockExpression::class.java) != null
                    ) {
                        return@mapNotNull null
                    }

                    updater.getWritable(expression)
                }.forEach {
                    val referencedName = it.getReferencedName()
                    it.replace(psiFactory.createExpression("$referencedName()"))
                }

            objectDeclaration.getObjectKeyword()?.replace(psiFactory.createClassKeyword())
        }
    }

}
