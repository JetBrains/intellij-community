// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.prevLeafs
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.AbstractKotlinGradleScriptInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.Replacement
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.getReplacementForOldKotlinOptionIfNeeded
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val dslsInWhichDontConvertKotlinOptions = listOf("android", "allprojects", "subprojects")

internal class KotlinOptionsToCompilerOptionsInGradleScriptInspection : AbstractKotlinGradleScriptInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean {
        if (super.isAvailableForFile(file)) {
            if (isUnitTestMode()) {
                // Inspection tests don't treat tested build script files properly, and thus they ignore Kotlin versions used in scripts
                return true
            } else {
                val jpsVersion = KotlinJpsPluginSettings.jpsVersion(file.project)
                val parsedKotlinVersion = IdeKotlinVersion.opt(jpsVersion)?.kotlinVersion ?: return false
                return parsedKotlinVersion >= KotlinVersion(2, 0, 0)
            }
        } else {
            return false
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            if (expression.text.equals("kotlinOptions")) {

                if (isDescendantOfDslInWhichReplacementIsNotNeeded(expression)) return

                val expressionParent = expression.parent
                when (expressionParent) {
                    is KtDotQualifiedExpression -> { // like `kotlinOptions.sourceMapEmbedSources`
                        val parentOfExpressionParent = expressionParent.parent
                        if (parentOfExpressionParent !is KtBinaryExpression) return // like kotlinOptions.sourceMapEmbedSources = "inlining"
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
                    }
                    else -> return
                }

                holder.problem(
                    expression,
                    KotlinBundle.message("inspection.kotlin.options.to.compiler.options.display.name")
                )
                    .highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    .range(
                        TextRange(
                            expression.startOffset,
                            expression.endOffset
                        ).shiftRight(-expression.startOffset)
                    )
                    .fix(
                        ReplaceKotlinOptionsWithCompilerOptionsFix()
                    ).register()
            }
        }
    }

    private fun isDescendantOfDslInWhichReplacementIsNotNeeded(ktExpression: KtExpression): Boolean {
        val scriptText = ktExpression.containingFile.text
        for (dslElement in dslsInWhichDontConvertKotlinOptions) {
            if (scriptText.contains(dslElement)) {
                ktExpression.prevLeafs.forEach {
                    if (dslElement == it.text) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

private class ReplaceKotlinOptionsWithCompilerOptionsFix() : KotlinModCommandQuickFix<KtExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("replace.kotlin.options.with.compiler.options")
    }

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {

        val expressionsToFix = mutableListOf<Replacement>()
        val expressionParent = element.parent
        when (expressionParent) {
            is KtDotQualifiedExpression -> { // for sth like `kotlinOptions.sourceMapEmbedSources`
                val parentOfExpressionParent = expressionParent.parent
                if (parentOfExpressionParent is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
                    getReplacementForOldKotlinOptionIfNeeded(parentOfExpressionParent)?.let { expressionsToFix.add(it) }
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
                if (lambdaStatements?.isNotEmpty() == true) { // compileKotlin.kotlinOptions { .. }
                    lambdaStatements.forEach {
                        searchAndProcessBinaryExpressionChildren(it, expressionsToFix)
                    }
                }
            }
        }

        expressionsToFix.forEach {
            val newExpression = KtPsiFactory(project).createExpression(it.replacement)
            val replacedElement = it.expressionToReplace.replaced(newExpression)

            if (it.classToImport != null) {
                (replacedElement.containingFile as? KtFile)?.addImport(it.classToImport)
            }
        }
    }

    /**
     * Test case:
     * K2LocalInspectionTestGenerated.InspectionsLocal.KotlinOptionsToCompilerOptions#testDontMergeConvertedOptionsToAnotherCompilerOptions_gradle
     */
    private fun searchAndProcessBinaryExpressionChildren(element: PsiElement, expressionsToFix: MutableList<Replacement>) {
        if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            getReplacementForOldKotlinOptionIfNeeded(element)?.let { expressionsToFix.add(it) }
        } else {
            element.children.forEach {
                searchAndProcessBinaryExpressionChildren(it, expressionsToFix)
            }
        }
    }
}

