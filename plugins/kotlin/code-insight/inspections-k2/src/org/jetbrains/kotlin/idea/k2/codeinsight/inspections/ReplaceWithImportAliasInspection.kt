// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.psi.simpleNameExpressionVisitor
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Suggests replacing qualified reference with an import alias when available.
 */
internal class ReplaceWithImportAliasInspection :
    KotlinApplicableInspectionBase.Simple<KtNameReferenceExpression, ReplaceWithImportAliasInspection.Context>() {

    data class Context(
        val aliasNameIdentifier: PsiElement
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor { expression ->
        if (expression is KtNameReferenceExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtNameReferenceExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replace.with.import.alias")

    override fun isApplicableByPsi(element: KtNameReferenceExpression): Boolean {
        if (element.getIdentifier() == null || element.isInImportDirective()) return false
        val qualifiedElement = element.getQualifiedElement()
        return qualifiedElement is KtDotQualifiedExpression || qualifiedElement is KtUserType
    }

    override fun KaSession.prepareContext(element: KtNameReferenceExpression): Context? {
        val name = element.getIdentifier()?.text ?: return null
        val ktFile = element.containingKtFile
        if (!ktFile.hasImportAlias()) return null

        val imports = ktFile.importDirectives.filter {
            !it.isAllUnder && it.alias != null && it.importedFqName?.shortName()?.asString() == name
        }.ifEmpty { return null }

        val fqName = element.mainReference.resolveToSymbols().firstOrNull()?.importableFqName ?: return null
        val aliasNameIdentifier = imports.find { it.importedFqName == fqName }?.alias?.nameIdentifier ?: return null
        
        return Context(
            aliasNameIdentifier = aliasNameIdentifier
        )
    }

    override fun createQuickFix(
        element: KtNameReferenceExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtNameReferenceExpression> = object : KotlinModCommandQuickFix<KtNameReferenceExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.import.alias")

        override fun getName(): String = KotlinBundle.message("replace.with.0", context.aliasNameIdentifier.text)

        override fun applyFix(
            project: Project,
            element: KtNameReferenceExpression,
            updater: ModPsiUpdater,
        ) {
            element.getIdentifier()?.replace(context.aliasNameIdentifier.copy())
            element.getQualifiedElement().let { qualifiedElement ->
                if (qualifiedElement is KtUserType) {
                    qualifiedElement.referenceExpression?.replace(element)
                    qualifiedElement.deleteQualifier()
                } else {
                    qualifiedElement.replace(element.parent.safeAs<KtCallExpression>() ?: element)
                }
            }
        }
    }
}
