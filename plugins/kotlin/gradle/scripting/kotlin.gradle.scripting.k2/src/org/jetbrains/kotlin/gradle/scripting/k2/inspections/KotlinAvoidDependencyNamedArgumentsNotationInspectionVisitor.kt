// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        val dependencyType = findDependencyType(expression) ?: return
        if (dependencyType != DependencyType.NAMED_ARGUMENTS) return
        val argList = expression.valueArgumentList ?: return
        if (!argList.isPhysical) return
        val args = argList.arguments
        // check that there are only 2-3 arguments and the named ones are among group, name or version
        // group and name arguments are always required and the third has to be named unless it's version
        if (args.size !in 2..3) return
        val ids = args.mapNotNull { it.getArgumentName()?.asName?.identifier }
        if (!setOf("group", "name", "version").containsAll(ids)) return

        holder.problem(
            argList,
            GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor")
        ).maybeFix(createPotentialFix(argList))
            .register()

    }

    private fun createPotentialFix(argList: KtValueArgumentList): GradleDependencyNamedArgumentsFix? {
        val group = findNamedOrPositionalArgument(argList, "group", 0)?.text ?: return null
        val name = findNamedOrPositionalArgument(argList, "name", 1)?.text ?: return null
        val version = findNamedOrPositionalArgument(argList, "version", 2)?.text

        // check that all arguments are single-line expressions
        if (group.contains('\n') || name.contains('\n') || version?.contains('\n') == true) return null

        val factory = KtPsiFactory(holder.project, true)
        val concatExpr =
            if (version != null) factory.createExpression("$group + \":\" + $name + \":\" + $version") as KtBinaryExpression
            else factory.createExpression("$group + \":\" + $name") as KtBinaryExpression

        return GradleDependencyNamedArgumentsFix(concatExpr)
    }
}

private class GradleDependencyNamedArgumentsFix(concatExpr: KtBinaryExpression) : KotlinModCommandQuickFix<KtValueArgumentList>() {
    private val concatExprPointer = concatExpr.createSmartPointer()

    override fun getName(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.simplify")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtValueArgumentList, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(project, true)
        val concatExpr = concatExprPointer.element ?: return
        val newArgument = analyze(concatExpr) {
            buildStringTemplateForBinaryExpression(concatExpr)
        }
        element.arguments.forEach { element.removeArgument(it) }
        element.addArgument(factory.createArgument(newArgument))
    }
}