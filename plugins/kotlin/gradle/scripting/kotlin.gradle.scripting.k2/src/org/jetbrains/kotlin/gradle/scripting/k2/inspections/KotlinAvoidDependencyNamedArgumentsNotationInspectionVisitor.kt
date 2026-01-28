// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleDependencyNamedArgumentsFix.Companion.buildSingleStringDependencyNotation

class KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        val dependencyType = findDependencyType(expression) ?: return
        if (dependencyType != DependencyType.NAMED_ARGUMENTS) return

        holder.problem(
            expression,
            GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor")
        ).range(expression.valueArgumentList?.textRangeInParent ?: expression.textRangeInParent)
            .maybeFix(createPotentialFix(expression))
            .register()
    }

    private fun createPotentialFix(callExpression: KtCallExpression): GradleDependencyNamedArgumentsFix? {
        val argList = callExpression.valueArgumentList ?: return null

        val group = findNamedOrPositionalArgument(argList, "group", 0)?.text ?: return null
        val name = findNamedOrPositionalArgument(argList, "name", 1)?.text ?: return null
        val version = findNamedOrPositionalArgument(argList, "version", 2)?.text
        val targetConfig = findNamedOrPositionalArgument(argList, "configuration", 3)?.text
        val classifier = findNamedOrPositionalArgument(argList, "classifier", 4)?.text
        val ext = findNamedOrPositionalArgument(argList, "ext", 5)?.text

        // check that all arguments are single-line expressions
        if (group.contains('\n') || name.contains('\n') || version?.contains('\n') == true ||
            classifier?.contains('\n') == true || ext?.contains('\n') == true
        ) return null

        val concat = buildSingleStringDependencyNotation(group, name, version, classifier, ext) ?: return null
        return GradleDependencyNamedArgumentsFix(concat, targetConfig)
    }
}

private class GradleDependencyNamedArgumentsFix(
    private val concat: String,
    private val targetConfig: String?
) : KotlinModCommandQuickFix<KtCallExpression>() {

    override fun getName(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.simplify")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val argList = element.valueArgumentList!!
        val factory = KtPsiFactory(project, true)
        val concatExpr = factory.createExpression(concat) as KtBinaryExpression
        val newArgument = analyze(concatExpr) {
            buildStringTemplateForBinaryExpression(concatExpr)
        }

        if (targetConfig == null) {
            replaceArguments(argList, factory, newArgument)
            return
        }

        val configBlock = element.getBlock()
        val targetConfigExpr = factory.createExpression("targetConfiguration = $targetConfig")

        if (configBlock != null) {
            val addedTarget = configBlock.addAfter(targetConfigExpr, null)
            configBlock.addAfter(factory.createNewLine(), addedTarget)
            replaceArguments(argList, factory, newArgument)
        } else {
            val newElement = factory.createExpression("${element.calleeExpression!!.text}(${newArgument.text}) {}") as KtCallExpression
            newElement.getBlock()!!.add(targetConfigExpr)
            element.replace(newElement)
        }
    }

    private fun replaceArguments(argList: KtValueArgumentList, factory: KtPsiFactory, newArgument: KtStringTemplateExpression) {
        argList.arguments.forEach { argList.removeArgument(it) }
        argList.addArgument(factory.createArgument(newArgument))
    }
}