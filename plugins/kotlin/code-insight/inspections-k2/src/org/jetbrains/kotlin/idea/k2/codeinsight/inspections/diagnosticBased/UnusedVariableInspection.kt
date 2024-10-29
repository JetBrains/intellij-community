// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.removeProperty
import org.jetbrains.kotlin.idea.codeinsight.utils.renameToUnderscore
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

internal class UnusedVariableInspection :
    KotlinDiagnosticBasedInspectionBase<KtNamedDeclaration, KaFirDiagnostic.UnusedVariable, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid {
        val file = holder.file
        return if (file !is KtFile || InjectedLanguageManager.getInstance(holder.project).isInjectedViewProvider(file.viewProvider)) {
            KtVisitorVoid()
        } else {
            object : KtVisitorVoid() {
                override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                    visitTargetElement(declaration, holder, isOnTheFly)
                }
            }
        }
    }

    override fun getProblemDescription(
        element: KtNamedDeclaration,
        context: Unit,
    ): String = KotlinBundle.message("inspection.kotlin.unused.variable.display.name")

    override val diagnosticType: KClass<KaFirDiagnostic.UnusedVariable>
        get() = KaFirDiagnostic.UnusedVariable::class

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    context(KaSession)
    override fun prepareContextByDiagnostic(
        element: KtNamedDeclaration,
        diagnostic: KaFirDiagnostic.UnusedVariable,
    ): Unit? {
        val ktProperty = diagnostic.psi as? KtCallableDeclaration ?: return null
        val typeReference = ktProperty.typeReference ?: return Unit
        return (!ktProperty.isExplicitTypeReferenceNeededForTypeInference(typeReference))
            .asUnit
    }

    override fun createQuickFix(
        element: KtNamedDeclaration,
        context: Unit,
    ): KotlinModCommandQuickFix<KtNamedDeclaration> {
        val smartPointer = element.createSmartPointer()

        return object : KotlinModCommandQuickFix<KtNamedDeclaration>() {

            override fun getFamilyName(): String =
                KotlinBundle.message("remove.variable")

            override fun getName(): String = getName(smartPointer) { element ->
                if (element is KtDestructuringDeclarationEntry) KotlinBundle.message("rename.to.underscore")
                else KotlinBundle.message("remove.variable.0", element.name.toString())
            }

            override fun applyFix(
                project: Project,
                element: KtNamedDeclaration,
                updater: ModPsiUpdater,
            ) {
                if (element is KtDestructuringDeclarationEntry) {
                    renameToUnderscore(element)
                } else if (element is KtProperty) {
                    removeProperty(element)
                }
            }
        }
    }
}