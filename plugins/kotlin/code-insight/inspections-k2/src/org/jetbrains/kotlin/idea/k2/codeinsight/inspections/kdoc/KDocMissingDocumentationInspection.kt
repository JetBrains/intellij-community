// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.kdoc

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.kdoc.findKDocByPsi
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedDeclarationVisitor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

internal class KDocMissingDocumentationInspection : KotlinApplicableInspectionBase.Simple<KtNamedDeclaration, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = namedDeclarationVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtNamedDeclaration): Boolean {
        if (element is KtParameter && element.isFunctionTypeParameter) return false
        return !TestUtils.isInTestSourceContent(element) && element.findKDocByPsi() == null
    }

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    override fun KaSession.prepareContext(element: KtNamedDeclaration): Unit? {
        val symbol = element.symbol.takeIf { isPublicApi(it) } ?: return null
        return (symbol is KaCallableSymbol && symbol.hasInheritedKDoc()).not().asUnit
    }

    override fun getProblemDescription(
        element: KtNamedDeclaration,
        context: Unit,
    ): @InspectionMessage String {
        return element.describe()?.let { KotlinBundle.message("0.is.missing.documentation", it) }
            ?: KotlinBundle.message("missing.documentation")
    }

    override fun createQuickFix(
        element: KtNamedDeclaration,
        context: Unit,
    ): KotlinModCommandQuickFix<KtNamedDeclaration> = object : KotlinModCommandQuickFix<KtNamedDeclaration>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("add.documentation.fix.text")

        override fun applyFix(
            project: Project,
            element: KtNamedDeclaration,
            updater: ModPsiUpdater,
        ) {
            element.addBefore(KDocElementFactory(project).createKDocFromText("/**\n* \n*/\n"), element.firstChild)

            val section = element.firstChild.getChildOfType<KDocSection>() ?: return
            val asterisk = section.firstChild

            updater.moveCaretTo(asterisk.endOffset + 1)
        }
    }
}

context(_: KaSession)
private fun KaCallableSymbol.hasInheritedKDoc(): Boolean =
    allOverriddenSymbols
        .mapNotNull { it.psi as? KtElement }
        .any { it.findKDocByPsi() != null }
