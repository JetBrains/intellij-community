// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    private val SCRIPT_DEPENDENCY_STATEMENTS = (
            SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS
                    + SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS.map {
                        "test" + it.first().uppercaseChar() + it.substring(1)
                    }
            )

    override fun visitScriptInitializer(initializer: KtScriptInitializer) {
        if (!initializer.text.startsWith("dependencies")) return
        val dependenciesCall = PsiTreeUtil.findChildOfType(initializer, KtCallExpression::class.java)
        val dependenciesBlock = (dependenciesCall?.valueArguments?.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
                ?: dependenciesCall?.lambdaArguments?.lastOrNull()?.getLambdaExpression()?.bodyExpression
        val dependencies = PsiTreeUtil.findChildrenOfType(dependenciesBlock, KtCallExpression::class.java).filter { call ->
            val calleeText = call.calleeExpression?.text
            // TODO only checks for standard configuration names
            calleeText in SCRIPT_DEPENDENCY_STATEMENTS
        }
        for (dep in dependencies) {
            val argList = dep.valueArgumentList ?: continue
            if (!argList.isPhysical) continue
            val args = argList.arguments
            val ids = args.map { it.getArgumentName()?.asName?.identifier }
            val values = args.map { it.getArgumentExpression()?.text }
            // check that there are only group, name and version named arguments
            if (ids.size != 3 && values.size != 3) continue
            if (ids.intersect(listOf("group", "name", "version")).size != 3) continue
            // check that all values are string literals
            for (value in values) {
                if (value == null) continue
                if (!value.startsWith("\"") || !value.endsWith("\"")) continue
            }
            holder.registerProblem(
                argList,
                GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
                ProblemHighlightType.WEAK_WARNING,
                GradleDependencyNamedArgumentsFix()
            )
        }
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
        val group = element.arguments.find { it.getArgumentName()?.asName?.identifier == "group" }?.getArgumentExpression()?.text?.removeSurrounding("\"")
            ?: return
        val name = element.arguments.find { it.getArgumentName()?.asName?.identifier== "name" }?.getArgumentExpression()?.text?.removeSurrounding("\"")
            ?: return
        val version = element.arguments.find { it.getArgumentName()?.asName?.identifier == "version" }?.getArgumentExpression()?.text?.removeSurrounding("\"")
            ?: return
        element.arguments.forEach { element.removeArgument(it) }
        element.addArgument(KtPsiFactory(project, true).createArgument("\"$group:$name:$version\""))
    }
}