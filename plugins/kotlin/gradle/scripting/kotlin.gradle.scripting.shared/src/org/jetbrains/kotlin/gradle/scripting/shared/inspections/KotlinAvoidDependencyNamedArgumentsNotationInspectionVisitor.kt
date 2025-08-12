// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY

class KotlinAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    private val GRADLE_DSL_PACKAGE = FqName("org.gradle.kotlin.dsl")

    override fun visitCallExpression(expression: KtCallExpression) {
        if (!isExternalDependencyDeclaration(expression)) return
        val argList = expression.valueArgumentList ?: return
        if (!argList.isPhysical) return
        val args = argList.arguments
        val ids = args.mapNotNull { it.getArgumentName()?.asName?.identifier }
        // check that there are only 3 arguments and the named ones are among group, name or version
        if (args.size !in 2..3 || !setOf("group", "name", "version").containsAll(ids)) return
        // check that all values are string literals
        val values = args.map { it.getArgumentExpression()?.text }
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

    private fun isExternalDependencyDeclaration(expression: KtCallExpression): Boolean {
        return analyze(expression) {
            val singleFunctionCallOrNull = expression.resolveToCall()?.singleFunctionCallOrNull()
            val symbol = singleFunctionCallOrNull?.symbol ?: return@analyze false
            symbol.callableId?.packageName == GRADLE_DSL_PACKAGE
                    && symbol.returnType.symbol?.classId?.asSingleFqName() == FqName(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
                    && symbol.valueParameters[0].name.identifier == "group"
                    && symbol.valueParameters[1].name.identifier == "name"
                    && symbol.valueParameters[2].name.identifier == "version"
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
        val group = findArgumentInDependency(element, "group", 0) ?: return
        val name = findArgumentInDependency(element, "name", 1) ?: return
        val version = findArgumentInDependency(element, "version", 2)
        element.arguments.forEach { element.removeArgument(it) }
        val stringNotationArgument = if (version != null) "\"$group:$name:$version\"" else "\"$group:$name\""
        element.addArgument(KtPsiFactory(project, true).createArgument(stringNotationArgument))
    }

    private fun findArgumentInDependency(element: KtValueArgumentList, parameterName: String, expectedIndex: Int): String? {
        val argument = element.arguments.find {
            it.getArgumentName()?.asName?.identifier == parameterName
        } ?: element.arguments.getOrNull(expectedIndex)
        return argument?.getArgumentExpression()?.text?.removeSurrounding("\"")
    }
}