// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
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
        // check that all values are string literals
        val values = args.map { it.getArgumentExpression() }
        if (values.any { it !is KtStringTemplateExpression }) return
        holder.registerProblem(
            argList,
            GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            GradleDependencyNamedArgumentsFix()
        )
    }
}

private class GradleDependencyNamedArgumentsFix() : KotlinModCommandQuickFix<KtValueArgumentList>() {
    override fun getName(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.simplify")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtValueArgumentList, updater: ModPsiUpdater) {
        val group = findNamedOrPositionalArgument(element, "group", 0)?.text?.removeSurrounding("\"") ?: return
        val name = findNamedOrPositionalArgument(element, "name", 1)?.text?.removeSurrounding("\"") ?: return
        val version = findNamedOrPositionalArgument(element, "version", 2)?.text?.removeSurrounding("\"")
        element.arguments.forEach { element.removeArgument(it) }
        val stringNotationArgument = if (version != null) "\"$group:$name:$version\"" else "\"$group:$name\""
        element.addArgument(KtPsiFactory(project, true).createArgument(stringNotationArgument))
    }
}