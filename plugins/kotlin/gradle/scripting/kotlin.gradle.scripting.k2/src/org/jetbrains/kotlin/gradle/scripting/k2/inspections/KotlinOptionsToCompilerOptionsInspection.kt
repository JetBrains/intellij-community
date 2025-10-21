// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.Replacement
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.containsNonReplaceableOperation
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getReplacementForOldKotlinOptionIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.kotlinVersionIsEqualOrHigher
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

private val kotlinCompileTasksNames = setOf(
    "org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile",
    "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
    "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile",
    "org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile"
)

internal class KotlinOptionsToCompilerOptionsInspection : AbstractKotlinInspection() {
    override fun isAvailableForFile(file: PsiFile): Boolean {
        val virtualFile = (file as? KtFile)?.alwaysVirtualFile ?: return false
        if (virtualFile.name == "settings.gradle.kts") return false
        return virtualFile.name.endsWith(".gradle.kts")
                && (ApplicationManager.getApplication().isUnitTestMode() // Inspection tests don't treat tested build script files properly, and thus they ignore Kotlin versions used in scripts
                || kotlinVersionIsEqualOrHigher(major = 2, minor = 0, patch = 0, file))
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid {
        return object : KtVisitorVoid() {

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                val referenceExpression = expression.receiverExpression.referenceExpression() ?: return

                if (!isKotlinOptionsOfNeededType(referenceExpression)) return

                val expressionParent = expression.parent
                if (elementCantBeProcessed(expressionParent)) return

                addProblemToHolder(referenceExpression)
            }

            override fun visitCallExpression(callExpression: KtCallExpression) {
                val referenceExpression = callExpression.referenceExpression() ?: return

                if (!isKotlinOptionsOfNeededType(referenceExpression)) return

                val lambdaStatements = callExpression.lambdaArguments.firstOrNull()
                    ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

                if (lambdaStatements?.any(::expressionsContainForbiddenOperations) == true) return
                addProblemToHolder(referenceExpression)
            }

            private fun addProblemToHolder(referenceExpression: KtReferenceExpression) {
                holder.problem(
                    referenceExpression,
                    KotlinBundle.message("inspection.kotlin.options.to.compiler.options.display.name")
                )
                    .highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    .fix(
                        ReplaceKotlinOptionsWithCompilerOptionsFix()
                    ).register()
            }
        }
    }

    private fun isKotlinOptionsOfNeededType(referenceExpression: KtReferenceExpression): Boolean {
        val referencedName = (referenceExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return false
        if (referencedName != "kotlinOptions") return false

        // ATM, we don't have proper dependencies for tests to perform `analyze` in Gradle build scripts
        return ApplicationManager.getApplication().isUnitTestMode() || kotlinOptionsAreOfNeededType(referenceExpression)
    }

    @OptIn(KaIdeApi::class)
    private fun kotlinOptionsAreOfNeededType(referenceExpression: KtReferenceExpression): Boolean {
        val jvmClassForKotlinCompileTask = analyze(referenceExpression) {
            val symbol = referenceExpression.resolveToCall()
                ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.signature?.symbol
            val containingDeclarationOrSymbol =
                (symbol?.containingDeclaration as? KaClassLikeSymbol)
                    ?: referenceExpression.resolveExpression()?.containingSymbol
            containingDeclarationOrSymbol?.importableFqName?.toString()
        }
        return jvmClassForKotlinCompileTask in kotlinCompileTasksNames
    }

    private fun elementCantBeProcessed(psiElement: PsiElement): Boolean = when (psiElement) {
        is KtBinaryExpression -> psiElement.containsNonReplaceableOperation()
        is KtDotQualifiedExpression -> psiElement.containsNonReplaceableOperation()
        else -> true
    }

    private fun expressionsContainForbiddenOperations(element: PsiElement): Boolean {
        return if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            element.containsNonReplaceableOperation()
        } else {
            element.children.any { expressionsContainForbiddenOperations(it) }
        }
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
                expressionsToFix.add(Replacement(element, "compilerOptions"))

                val lambdaStatements = expressionParent.lambdaArguments.firstOrNull()
                    ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

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