// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER

private const val DESCRIPTION_PROPERTY = "description"

class KotlinTaskMissingDescriptionInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (expression.calleeExpression?.text !in setOf("register", "create", "registering", "creating")) return
        if (!expression.isTaskContainerReceiver()) return

        val blockExpression = expression.getBlock()
        if (blockExpression != null) checkConfigBlockAndReport(expression, blockExpression)
        else reportCallNoConfigBlock(expression)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        if (expression.text !in setOf("registering", "creating")) return
        if (expression !is KtNameReferenceExpression || expression.parent is KtCallExpression) return
        if (!expression.isTaskContainerReceiver()) return

        reportReference(expression)
    }

    private fun KtExpression.isTaskContainerReceiver(): Boolean {
        val containingClassSymbol = this.getReceiverClassId() ?: return false
        return isInheritor(this, containingClassSymbol, GRADLE_API_TASK_CONTAINER_CLASS_ID)
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
            val receiverClassId = it.left!!.getReceiverClassId() ?: return@any false
            isInheritor(it.left!!, receiverClassId, GRADLE_API_TASK_CLASS_ID)
        }

    private fun KtBlockExpression.hasDescriptionSetter(): Boolean = this.descendantsOfType<KtCallExpression>()
        .filter {
            val callName = it.calleeExpression?.text
            callName == DESCRIPTION_SETTER
        }.any {
            val receiverClassId = it.getReceiverClassId() ?: return@any false
            isInheritor(it, receiverClassId, GRADLE_API_TASK_CLASS_ID)
        }

    private fun KtExpression.getReceiverClassId(): ClassId? = analyze(this) {
        val resolvedCall = this@getReceiverClassId.resolveToCall() ?: return null
        val callPartiallyAppliedSymbol = resolvedCall.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol ?: return null
        val type = callPartiallyAppliedSymbol.extensionReceiver?.type
            ?: callPartiallyAppliedSymbol.dispatchReceiver?.type
        val unwrappedType = if (type is KaFlexibleType) type.lowerBound else type

        return unwrappedType?.symbol?.classId ?: callPartiallyAppliedSymbol.symbol.callableId?.classId
    }

    private fun isInheritor(useSiteElement: KtElement, targetClassId: ClassId, baseClassId: ClassId): Boolean {
        if (targetClassId == baseClassId) return true
        return analyze(useSiteElement) {
            val targetClass = findClass(targetClassId) ?: return@analyze false
            val baseClass = findClass(baseClassId) ?: return@analyze false
            return targetClass.isSubClassOf(baseClass)
        }
    }

    companion object {
        private const val DESCRIPTION_SETTER = "setDescription"
        private val GRADLE_API_TASK_CLASS_ID = ClassId.fromString(GRADLE_API_TASK.replace('.', '/'))
        private val GRADLE_API_TASK_CONTAINER_CLASS_ID = ClassId.fromString(GRADLE_API_TASK_CONTAINER.replace('.', '/'))
    }
}

private class AddDescriptionFix : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): String = familyName
    override fun getFamilyName(): @IntentionFamilyName String = GradleInspectionBundle.message("intention.name.task.add.description")

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        val block = element.getBlock() ?: return
        val psiFactory = KtPsiFactory(project, true)

        val assignment = psiFactory.createExpression("$DESCRIPTION_PROPERTY = \"\"")
        val emptyStringPos = block.addAfter(assignment, null)
            .apply { block.addAfter(psiFactory.createNewLine(), this) }
            .asSafely<KtBinaryExpression>()!!.right!!.textOffset

        updater.moveCaretTo(emptyStringPos + 1)
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

        val replacement = psiFactory.createExpression(
            """
            $selectorName {
                $DESCRIPTION_PROPERTY = ""
            }
            """.trimIndent()
        ) as KtCallExpression
        val replaced = element.replace(replacement) as KtCallExpression
        val emptyStringPos = replaced.getBlock()!!.children.map {
            it.asSafely<KtBinaryExpression>()!!.right!!
        }.single().textOffset

        updater.moveCaretTo(emptyStringPos + 1)
    }
}