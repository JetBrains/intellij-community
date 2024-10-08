// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.Replacement
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.expressionContainsOperationForbiddenToReplace
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.kotlinVersionIsEqualOrHigher
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getReplacementForOldKotlinOptionIfNeeded
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.AbstractKotlinGradleScriptInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.resolveExpression
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val kotlinCompileTasksNames = setOf(
    "org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile",
    "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
    "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile",
    "org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile"
)

internal class KotlinOptionsToCompilerOptionsInGradleScriptInspection : AbstractKotlinGradleScriptInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return if (super.isAvailableForFile(file)) {
            if (isUnitTestMode()) {
                // Inspection tests don't treat tested build script files properly, and thus they ignore Kotlin versions used in scripts
                true
            } else {
                kotlinVersionIsEqualOrHigher(major = 2, minor = 0, patch = 0, file)
            }
        } else {
            false
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                val referencedName = (expression as? KtNameReferenceExpression)?.getReferencedName() ?: return
                // ATM, we don't have proper dependencies for tests to perform `analyze` in Gradle build scripts
                if (referencedName == "android" && !isUnitTestMode()) {
                    if (elementIsAndroidDsl(expression)) return
                }
                if (referencedName != "kotlinOptions") return

                val expressionParent = expression.parent

                if (!isUnitTestMode()) { // ATM, we don't have proper dependencies for tests to perform `analyze` in Gradle build scripts
                    val jvmClassForKotlinCompileTask = analyze(expression) {
                        val symbol = expression.resolveToCall()
                            ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.signature?.symbol
                        val containingDeclarationOrSymbol =
                            (symbol?.containingDeclaration as? KaClassLikeSymbol) ?: expression.resolveExpression()?.containingSymbol
                        containingDeclarationOrSymbol?.importableFqName?.toString()
                    }
                    if (jvmClassForKotlinCompileTask !in kotlinCompileTasksNames) {
                        return
                    }
                }
                when (expressionParent) {
                    is KtDotQualifiedExpression -> { // like `kotlinOptions.sourceMapEmbedSources` OR kotlinOptions.options
                        val parentOfExpressionParent = expressionParent.parent
                        if (elementContainsOperationForbiddenToReplaceOrCantBeProcessed(parentOfExpressionParent)) return
                    }

                    is KtCallExpression -> {
                        /*
                    Like the following. Raise a problem for this.
                    compileKotlin.kotlinOptions {
                        jvmTarget = "1.8"
                        freeCompilerArgs += listOf("-module-name", "TheName")
                        apiVersion = "1.9"
                    }
                     */
                        val lambdaStatements = expressionParent.lambdaArguments.firstOrNull()
                            ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

                        // compileKotlin.kotlinOptions { .. }
                        lambdaStatements?.forEach {
                            if (expressionsContainForbiddenOperations(it)) return
                        }
                    }

                    else -> return
                }

                holder.problem(
                    expression,
                    KotlinBundle.message("inspection.kotlin.options.to.compiler.options.display.name")
                )
                    .highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    .fix(
                        ReplaceKotlinOptionsWithCompilerOptionsFix()
                    ).register()
            }
        }
    }

    private fun elementContainsOperationForbiddenToReplaceOrCantBeProcessed(psiElement: PsiElement): Boolean {
        return when (psiElement) {
            is KtBinaryExpression -> {
                expressionContainsOperationForbiddenToReplace(psiElement)
            }

            is KtDotQualifiedExpression -> {
                val psiElementParent = psiElement.parent
                if (psiElementParent is KtBinaryExpression) {
                    expressionContainsOperationForbiddenToReplace(psiElementParent)
                } else { // Can't be processed
                    true
                }
            }

            else -> true
        }
    }

    private fun elementIsAndroidDsl(expression: KtExpression): Boolean {
        val importableFqName = analyze(expression) {
            val symbol = expression.resolveToCall()
                ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.signature?.symbol
            val kaSymbol =
                (symbol?.containingDeclaration as? KaClassLikeSymbol) ?: expression.resolveExpression()
            kaSymbol?.importableFqName?.toString()
        }
        return importableFqName == "org.gradle.kotlin.dsl.android"
    }

    private fun expressionsContainForbiddenOperations(element: PsiElement): Boolean {
        if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            if (expressionContainsOperationForbiddenToReplace(element)) return true
        } else {
            return element.children.any { expressionsContainForbiddenOperations(it) }
        }
        return false
    }
}

private class ReplaceKotlinOptionsWithCompilerOptionsFix() : KotlinModCommandQuickFix<KtExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.kotlin.options.with.compiler.options")

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater,
    ) {
        val expressionsToFix = mutableListOf<Replacement>()
        val expressionParent = element.parent
        when (expressionParent) {
            is KtDotQualifiedExpression -> { // for sth like `kotlinOptions.sourceMapEmbedSources` || `kotlinOptions.options.jvmTarget`
                val parentOfExpressionParent = expressionParent.parent
                when (parentOfExpressionParent) {
                    is KtBinaryExpression -> { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
                        getReplacementForOldKotlinOptionIfNeeded(parentOfExpressionParent)?.let { expressionsToFix.add(it) }
                    }

                    is KtDotQualifiedExpression -> {
                        val parent = parentOfExpressionParent.parent
                        if (parent is KtBinaryExpression) { // like `kotlinOptions.options.jvmTarget = JvmTarget.JVM_11`
                            getReplacementForOldKotlinOptionIfNeeded(parent)?.let { expressionsToFix.add(it) }
                        }
                    }
                }
            }

            is KtCallExpression -> {
                /* Example:
                compileKotlin.kotlinOptions {
                    jvmTarget = "1.8"
                    freeCompilerArgs += listOf("-module-name", "TheName")
                    apiVersion = "1.9"
                }

                OR
                tasks.withType<KotlinCompile> {
                    kotlinOptions {
                        freeCompilerArgs += listOf("-module-name", "TheName")
                    }
                }
                */

                expressionsToFix.add(Replacement(element, "compilerOptions"))

                val lambdaStatements = expressionParent.lambdaArguments.getOrNull(0)
                    ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

                /**
                 * Test case:
                 * K2LocalInspectionTestGenerated.InspectionsLocal.KotlinOptionsToCompilerOptions#testLambdaWithSeveralStatements_gradle())
                 */
                // compileKotlin.kotlinOptions { .. }
                lambdaStatements?.forEach {
                    addExpressionsToFixIfNeeded(it, expressionsToFix)
                }
            }
        }

        val file = element.containingFile as? KtFile ?: return
        val ktPsiFactory = KtPsiFactory(project)
        expressionsToFix.forEach {
            val newExpression = ktPsiFactory.createExpression(it.replacement)
            it.expressionToReplace.replaced(newExpression)

            val classToImport = it.classToImport
            if (classToImport != null) {
                file.addImport(classToImport)
            }
        }
    }

    /**
     * Test case:
     * K2LocalInspectionTestGenerated.InspectionsLocal.KotlinOptionsToCompilerOptions#testDontMergeConvertedOptionsToAnotherCompilerOptions_gradle
     */
    private fun addExpressionsToFixIfNeeded(element: PsiElement, expressionsToFix: MutableList<Replacement>) {
        if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            getReplacementForOldKotlinOptionIfNeeded(element)?.let { expressionsToFix.add(it) }
        } else {
            element.children.forEach {
                addExpressionsToFixIfNeeded(it, expressionsToFix)
            }
        }
    }
}

