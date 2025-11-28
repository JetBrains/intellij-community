// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER

private const val DESCRIPTION_PROPERTY = "description"

class KotlinTaskMissingDescriptionInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            when (expression.calleeExpression?.text) {
                "register", "create" -> {
                    val callableId = expression.resolveExpression().asSafely<KaCallableSymbol>()?.callableId ?: return
                    if (callableId.classId?.asSingleFqName() !in setOf(
                            FqName(GRADLE_API_TASK_CONTAINER),
                            GRADLE_KOTLIN_TASK_CONTAINER_DELEGATE
                        )
                    ) return
                }

                "registering", "creating" -> {
                    val callableId = expression.resolveExpression().asSafely<KaCallableSymbol>()?.callableId ?: return
                    if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return
                }

                else -> return
            }
        }

        val blockExpression = expression.getBlock()
        if (blockExpression != null) checkConfigBlockAndReport(expression, blockExpression)
        else reportCallNoConfigBlock(expression)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        if (expression.text !in setOf("registering", "creating")) return
        if (expression !is KtNameReferenceExpression || expression.parent is KtCallExpression) return
        analyze(expression) {
            val callableId = expression.resolveExpression().asSafely<KaCallableSymbol>()?.callableId ?: return
            if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return
        }

        reportReference(expression)
    }

    private fun checkConfigBlockAndReport(callExpression: KtCallExpression, blockExpression: KtBlockExpression) {
        if (blockExpression.hasDescriptionAssignment() || blockExpression.hasDescriptionSetter()) return

        holder.problem(
            callExpression,
            GradleInspectionBundle.message("inspection.message.task.missing.description.descriptor")
        ).range(callExpression.calleeExpression?.textRangeInParent ?: callExpression.textRangeInParent)
            .fix(AddDescriptionFix())
            .register()
    }

    private fun reportCallNoConfigBlock(callExpression: KtCallExpression) {
        holder.problem(
            callExpression,
            GradleInspectionBundle.message("inspection.message.task.missing.description.descriptor")
        ).range(callExpression.calleeExpression?.textRangeInParent ?: callExpression.textRangeInParent)
            .fix(AddConfigBlockWithDescriptionFix())
            .register()
    }

    private fun reportReference(nameReferenceExpression: KtNameReferenceExpression) {
        holder.problem(
            nameReferenceExpression,
            GradleInspectionBundle.message("inspection.message.task.missing.description.descriptor")
        ).fix(AddConfigBlockWithDescriptionFix())
            .register()
    }

    private fun KtBlockExpression.hasDescriptionAssignment(): Boolean = this.descendantsOfType<KtBinaryExpression>()
        .filter {
            val propertyName = it.left?.text
            propertyName == DESCRIPTION_PROPERTY
        }
        .filter { it.operationReference.node.findChildByType(BinaryOperationPrecedence.ASSIGNMENT.tokenSet) != null }
        .any {
            analyze(it) {
                val parentPackage = it.left?.resolveExpression()?.getFqNameIfPackageOrNonLocal()?.parentOrNull()
                parentPackage.toString().startsWith(GRADLE_API_COMMON_PACKAGE) || parentPackage == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }

    private fun KtBlockExpression.hasDescriptionSetter(): Boolean = this.descendantsOfType<KtCallExpression>()
        .filter {
            val callName = it.calleeExpression?.text
            callName == DESCRIPTION_SETTER
        }.any {
            analyze(it) {
                val classId = it.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.classId?.asSingleFqName()
                    ?: return@analyze false
                classId.toString().startsWith(GRADLE_API_COMMON_PACKAGE) || classId == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }

    companion object {
        private const val DESCRIPTION_SETTER = "setDescription"
        private const val GRADLE_API_COMMON_PACKAGE = "org.gradle.api"
        private val GRADLE_KOTLIN_PROJECT_DELEGATE = FqName("org.gradle.kotlin.dsl.support.delegates.ProjectDelegate")
        private val GRADLE_KOTLIN_TASK_CONTAINER_DELEGATE = FqName("org.gradle.kotlin.dsl.support.delegates.TaskContainerDelegate")
    }
}

private class AddDescriptionFix() : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): String = familyName
    override fun getFamilyName(): @IntentionFamilyName String = GradleInspectionBundle.message("intention.name.task.add.description")

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        val block = element.getBlock() ?: return
        val psiFactory = KtPsiFactory(project, true)
        val templateBuilder = updater.templateBuilder()

        val assignment = psiFactory.createExpression("${DESCRIPTION_PROPERTY} = \"\"")
        val templateElement = block.addAfter(assignment, null)
            .apply { block.addAfter(psiFactory.createNewLine(), this) }
            .asSafely<KtBinaryExpression>()!!.right!!

        templateBuilder.field(
            templateElement,
            TextRange(1, 1),
            "descriptionField",
            ConstantNode("")
        )
    }
}

private class AddConfigBlockWithDescriptionFix() : KotlinModCommandQuickFix<KtElement>() {
    override fun getName(): String = familyName
    override fun getFamilyName(): @IntentionFamilyName String = GradleInspectionBundle.message("intention.name.task.add.description")

    override fun applyFix(
        project: Project,
        element: KtElement,
        updater: ModPsiUpdater
    ) {
        val selectorName = element.text
        val psiFactory = KtPsiFactory(project, true)
        val templateBuilder = updater.templateBuilder()
        val replacement = psiFactory.createExpression(
            """
            $selectorName {
                ${DESCRIPTION_PROPERTY} = ""
            }
            """.trimIndent()
        ) as KtCallExpression
        val replaced = element.replace(replacement) as KtCallExpression
        val templateDescriptionElement = replaced.getBlock()!!.children.map {
            it.asSafely<KtBinaryExpression>()!!.right!!
        }.single()

        templateBuilder.field(
            templateDescriptionElement,
            TextRange(1, 1),
            "descriptionField",
            ConstantNode("")
        )
    }
}