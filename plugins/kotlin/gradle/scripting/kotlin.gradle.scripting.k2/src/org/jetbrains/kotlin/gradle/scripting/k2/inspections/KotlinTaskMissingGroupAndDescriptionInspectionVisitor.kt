// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinTaskMissingGroupAndDescriptionInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val callableId = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return
            if (callableId.callableName.asString() !in TASK_CONTAINER_CREATION_METHODS) return
            val classId = callableId.classId ?: return
            if (classId.asSingleFqName() != GRADLE_TASKS_CONTAINER) return
        }
        val calleeExpression = expression.getCalleeExpression() ?: return
        val taskBlock = expression.getBlock() ?: return

        // find if group or description are already set
        val alreadySetProperties = taskBlock.findPropertyAssignments() + taskBlock.findSetterCalls()
        if (alreadySetProperties.size >= 2) return

        val message = when (alreadySetProperties) {
            setOf(Property.GROUP) ->
                GradleInspectionBundle.message("inspection.message.task.missing.group.or.description.descriptor", "description")

            setOf(Property.DESCRIPTION) ->
                GradleInspectionBundle.message("inspection.message.task.missing.group.or.description.descriptor", "group")

            emptySet<Property>() ->
                GradleInspectionBundle.message("inspection.message.task.missing.group.and.description.descriptor")

            else -> return
        }

        holder.problem(calleeExpression, message).register()

    }

    private fun KtBlockExpression.findPropertyAssignments(): Set<Property> = this.descendantsOfType<KtBinaryExpression>()
        .filter { it.left?.text == "group" || it.left?.text == "description" }
        .filter { it.operationReference.node.findChildByType(BinaryOperationPrecedence.ASSIGNMENT.tokenSet) != null }
        .filter {
            analyze(it) {
                it.left?.resolveExpression()?.getFqNameIfPackageOrNonLocal()?.parentOrNull() == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }.mapNotNull {
            when (it.left?.text) {
                "group" -> Property.GROUP
                "description" -> Property.DESCRIPTION
                else -> null
            }
        }.toSet()

    private fun KtBlockExpression.findSetterCalls(): Set<Property> = this.descendantsOfType<KtCallExpression>()
        .filter { it.calleeExpression?.text == "setGroup" || it.calleeExpression?.text == "setDescription" }
        .filter {
            analyze(it) {
                val classId = it.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.classId ?: return@analyze false
                classId.asSingleFqName() == GRADLE_KOTLIN_PROJECT_DELEGATE
            }
        }.mapNotNull {
            when (it.calleeExpression?.text) {
                "setGroup" -> Property.GROUP
                "setDescription" -> Property.DESCRIPTION
                else -> null
            }
        }.toSet()

    companion object {
        private val GRADLE_TASKS_CONTAINER = FqName("org.gradle.api.tasks.TaskContainer")
        private val GRADLE_KOTLIN_PROJECT_DELEGATE = FqName("org.gradle.kotlin.dsl.support.delegates.ProjectDelegate")
        private val TASK_CONTAINER_CREATION_METHODS = setOf("register", "create", "registering", "creating")

        private enum class Property {
            GROUP, DESCRIPTION
        }
    }
}
