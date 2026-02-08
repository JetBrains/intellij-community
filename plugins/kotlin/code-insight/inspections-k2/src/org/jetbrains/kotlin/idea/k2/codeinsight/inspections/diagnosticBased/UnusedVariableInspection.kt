// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isExplicitTypeReferenceNeededForTypeInference
import org.jetbrains.kotlin.idea.codeinsight.utils.renameToUnderscore
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.reflect.KClass

internal class UnusedVariableInspection :
    KotlinKtDiagnosticBasedInspectionBase<KtNamedDeclaration, KaFirDiagnostic.UnusedVariable, UnusedVariableInspection.Context>() {

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
        context: Context,
    ): String = KotlinBundle.message("inspection.kotlin.unused.variable.display.name")

    override val diagnosticType: KClass<KaFirDiagnostic.UnusedVariable>
        get() = KaFirDiagnostic.UnusedVariable::class

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    class Context(
        val couldBeAnExplicitlyIgnoredValue: Boolean,
        val isSimpleCase: Boolean
    )

    override fun KaSession.prepareContextByDiagnostic(
        element: KtNamedDeclaration,
        diagnostic: KaFirDiagnostic.UnusedVariable,
    ): Context? {
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return null
        val couldBeAnExplicitlyIgnoredValue =
            (declaration is KtProperty)
                    && !declaration.isVar
                    && element.languageVersionSettings.supportsFeature(LanguageFeature.UnnamedLocalVariables)
                    && !declaration.symbol.returnType.isUnitType
        
        val isSimpleCase = isSimpleCaseVariable(declaration)
        val typeReference = declaration.typeReference ?: return Context(couldBeAnExplicitlyIgnoredValue, isSimpleCase)
        return if (!declaration.isExplicitTypeReferenceNeededForTypeInference(typeReference)) {
            Context(couldBeAnExplicitlyIgnoredValue, isSimpleCase)
        } else {
            null
        }
    }

    private fun isSimpleCaseVariable(declaration: KtCallableDeclaration): Boolean {
        if (declaration !is KtProperty) return false
        
        val initializer = declaration.initializer ?: return false
        
        return when (initializer) {
            // Literals: val x = 5, val s = "string", val b = true
            is KtConstantExpression -> true
            is KtStringTemplateExpression -> {
                // Simple string literals without interpolation
                initializer.entries.all { it is KtLiteralStringTemplateEntry }
            }
            // Simple property access or function calls that are likely pure
            is KtDotQualifiedExpression -> {
                // Simple cases like SomeClass.CONSTANT or obj.pureFunction()
                initializer.isPure()
            }
            is KtNameReferenceExpression -> {
                // Reference to another variable/constant
                true
            }
            else -> false
        }
    }

    override fun createQuickFix(
        element: KtNamedDeclaration,
        context: Context,
    ): KotlinModCommandQuickFix<KtNamedDeclaration> {
        val smartPointer = element.createSmartPointer()
        return object : KotlinModCommandQuickFix<KtNamedDeclaration>() {

            override fun getFamilyName(): String =
                KotlinBundle.message("remove.variable")

            override fun getName(): String = getName(smartPointer) { element ->
                when (element) {
                    is KtDestructuringDeclarationEntry -> {
                        KotlinBundle.message("rename.to.underscore")
                    }

                    is KtProperty if context.couldBeAnExplicitlyIgnoredValue -> {
                        KotlinBundle.message("rename.0.to.explicitly.ignore.return.value", element.name.toString())
                    }

                    else -> {
                        if (context.isSimpleCase) {
                            KotlinBundle.message("remove.variable.0", element.name.toString())
                        } else {
                            KotlinBundle.message("remove.variable.change.semantics", element.name.toString())
                        }
                    }
                }
            }

            override fun applyFix(
                project: Project,
                element: KtNamedDeclaration,
                updater: ModPsiUpdater,
            ) {
                when (element) {
                    is KtDestructuringDeclarationEntry -> renameToUnderscore(element)
                    is KtProperty -> {
                        if (context.couldBeAnExplicitlyIgnoredValue) {
                            renameToUnderscore(element)
                        } else {
                            // Always remove the entire statement for both simple and complex cases
                            element.delete()
                        }
                    }
                }
            }
        }
    }
}