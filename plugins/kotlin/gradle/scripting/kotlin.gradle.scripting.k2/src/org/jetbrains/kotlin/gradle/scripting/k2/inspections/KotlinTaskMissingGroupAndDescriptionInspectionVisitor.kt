// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
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
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER

private enum class TaskProperty(val propertyName: String, val setterName: String) {
    GROUP("group", "setGroup"),
    DESCRIPTION("description", "setDescription")
}

class KotlinTaskMissingGroupAndDescriptionInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        if (expression.receiverExpression.text != "tasks") return
        val selectorExpression = expression.selectorExpression ?: return
        val selectorName = when (selectorExpression) {
            is KtCallExpression -> selectorExpression.calleeExpression?.text
            is KtNameReferenceExpression -> selectorExpression.text
            else -> return
        }

        analyze(expression) {
            when (selectorName) {
                "register", "create" -> {
                    val callableId = selectorExpression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return
                    if (callableId.classId?.asSingleFqName() != FqName(GRADLE_API_TASK_CONTAINER)) return
                }

                "registering", "creating" -> {
                    val tasksClassId = expression.receiverExpression.resolveExpression()
                        .asSafely<KaCallableSymbol>()?.callableId?.classId?.asSingleFqName() ?: return
                    if (tasksClassId != GRADLE_KOTLIN_PROJECT_DELEGATE &&
                        !tasksClassId.toString().startsWith(GRADLE_API_COMMON_PACKAGE)
                    ) return
                }

                else -> return
            }
        }

        when (selectorExpression) {
            is KtCallExpression -> {
                val blockExpression = selectorExpression.getBlock()
                if (blockExpression != null) checkConfigBlockAndReport(selectorExpression, blockExpression)
                else reportCallNoConfigBlock(selectorExpression)
            }

            is KtNameReferenceExpression -> reportReference(selectorExpression)
        }
    }

    private fun checkConfigBlockAndReport(callExpression: KtCallExpression, blockExpression: KtBlockExpression) {
        // find if group or description properties are already set
        val alreadySetProperties = blockExpression.findPropertyAssignments() + blockExpression.findSetterCalls()
        if (alreadySetProperties.size >= 2) return

        val (message, fix) = when (alreadySetProperties) {
            setOf(TaskProperty.GROUP) -> GradleInspectionBundle.message(
                "inspection.message.task.missing.group.or.description.descriptor",
                TaskProperty.DESCRIPTION.propertyName
            ) to AddGroupDescriptionFix(addGroup = false, addDescription = true)

            setOf(TaskProperty.DESCRIPTION) -> GradleInspectionBundle.message(
                "inspection.message.task.missing.group.or.description.descriptor",
                TaskProperty.GROUP.propertyName
            ) to AddGroupDescriptionFix(addGroup = true, addDescription = false)

            emptySet<TaskProperty>() ->
                GradleInspectionBundle.message("inspection.message.task.missing.group.and.description.descriptor") to
                        AddGroupDescriptionFix(addGroup = true, addDescription = true)

            else -> return
        }

        holder.problem(callExpression, message)
            .range(callExpression.calleeExpression?.textRangeInParent ?: callExpression.textRangeInParent)
            .fix(fix).register()
    }

    private fun reportCallNoConfigBlock(callExpression: KtCallExpression) {
        holder.problem(
            callExpression,
            GradleInspectionBundle.message("inspection.message.task.missing.group.and.description.descriptor")
        ).range(callExpression.calleeExpression?.textRangeInParent ?: callExpression.textRangeInParent)
            .fix(AddConfigBlockWithGroupDescriptionFix()).register()
    }

    private fun reportReference(nameReferenceExpression: KtNameReferenceExpression) {
        holder.problem(
            nameReferenceExpression,
            GradleInspectionBundle.message("inspection.message.task.missing.group.and.description.descriptor")
        ).fix(AddConfigBlockWithGroupDescriptionFix()).register()
    }

    private fun KtBlockExpression.findPropertyAssignments(): Set<TaskProperty> = this.descendantsOfType<KtBinaryExpression>()
        .filter {
            val propertyName = it.left?.text
            propertyName == TaskProperty.GROUP.propertyName || propertyName == TaskProperty.DESCRIPTION.propertyName
        }
        .filter { it.operationReference.node.findChildByType(BinaryOperationPrecedence.ASSIGNMENT.tokenSet) != null }
        .filter {
            analyze(it) {
                val parentPackage = it.left?.resolveExpression()?.getFqNameIfPackageOrNonLocal()?.parentOrNull()
                parentPackage.toString().startsWith(GRADLE_API_COMMON_PACKAGE) || parentPackage == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }.mapNotNull {
            when (it.left?.text) {
                TaskProperty.GROUP.propertyName -> TaskProperty.GROUP
                TaskProperty.DESCRIPTION.propertyName -> TaskProperty.DESCRIPTION
                else -> null
            }
        }.toSet()

    private fun KtBlockExpression.findSetterCalls(): Set<TaskProperty> = this.descendantsOfType<KtCallExpression>()
        .filter {
            val callName = it.calleeExpression?.text
            callName == TaskProperty.GROUP.setterName || callName == TaskProperty.DESCRIPTION.setterName
        }.filter {
            analyze(it) {
                val classId = it.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.classId?.asSingleFqName()
                    ?: return@analyze false
                classId.toString().startsWith(GRADLE_API_COMMON_PACKAGE) || classId == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }.mapNotNull {
            when (it.calleeExpression?.text) {
                TaskProperty.GROUP.setterName -> TaskProperty.GROUP
                TaskProperty.DESCRIPTION.setterName -> TaskProperty.DESCRIPTION
                else -> null
            }
        }.toSet()

    companion object {
        private const val GRADLE_API_COMMON_PACKAGE = "org.gradle.api"
        private val GRADLE_KOTLIN_PROJECT_DELEGATE = FqName("org.gradle.kotlin.dsl.support.delegates.ProjectDelegate")
    }
}

private class AddGroupDescriptionFix(
    private val addGroup: Boolean,
    private val addDescription: Boolean
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): String =
        if (addGroup && addDescription) GradleInspectionBundle.message("intention.name.task.missing.group.and.description")
        else if (addGroup) GradleInspectionBundle.message(
            "intention.name.task.missing.group.or.description",
            TaskProperty.GROUP.propertyName
        )
        else GradleInspectionBundle.message("intention.name.task.missing.group.or.description", TaskProperty.DESCRIPTION.propertyName)

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.task.missing.group.and.description")

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        val block = element.getBlock() ?: return
        val psiFactory = KtPsiFactory(project, true)
        val templateBuilder = updater.templateBuilder()

        if (addGroup) {
            val assignment = psiFactory.createExpression("${TaskProperty.GROUP.propertyName} = \"\"")
            val templateElement = block.addAfter(assignment, null)
                .apply { block.addAfter(psiFactory.createNewLine(), this) }
                .asSafely<KtBinaryExpression>()!!.right!!

            templateBuilder.field(
                templateElement,
                TextRange(1, 1),
                "groupField",
                ConstantNode("").withLookupStrings(getTaskGroups(project))
            )
        }
        if (addDescription) {
            val assignment = psiFactory.createExpression("${TaskProperty.DESCRIPTION.propertyName} = \"\"")
            val anchor = if (addGroup) block.firstChild else null
            val templateElement = block.addAfter(assignment, anchor)
                .apply {
                    if (addGroup) block.addBefore(psiFactory.createNewLine(), this)
                    else block.addAfter(psiFactory.createNewLine(), this)
                }.asSafely<KtBinaryExpression>()!!.right!!
            templateBuilder.field(
                templateElement,
                TextRange(1, 1),
                "descriptionField",
                ConstantNode("")
            )
        }
    }
}

private class AddConfigBlockWithGroupDescriptionFix() : KotlinModCommandQuickFix<KtElement>() {
    override fun getName(): String =
        GradleInspectionBundle.message("intention.name.task.missing.group.and.description")

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.task.missing.group.and.description")

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
                ${TaskProperty.GROUP.propertyName} = ""
                ${TaskProperty.DESCRIPTION.propertyName} = ""
            }
            """.trimIndent()
        ) as KtCallExpression
        val replaced = element.replace(replacement) as KtCallExpression
        val (templateGroupElement, templateDescriptionElement) = replaced.getBlock()!!.children.map {
            it.asSafely<KtBinaryExpression>()!!.right!!
        }

        templateBuilder.field(
            templateGroupElement,
            TextRange(1, 1),
            "groupField",
            ConstantNode("").withLookupStrings(getTaskGroups(project))
        )
        templateBuilder.field(
            templateDescriptionElement,
            TextRange(1, 1),
            "descriptionField",
            ConstantNode("")
        )
    }
}

private fun getTaskGroups(project: Project) = GradleTasksIndices.getInstance(project)
    .findTasks(project.guessProjectDir()!!.path)
    .mapNotNull { it.group }.toSet().sortedBy { it.lowercase() }