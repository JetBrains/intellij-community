// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinFirConstantExpressionEvaluator
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor

class KotlinAvoidDuplicateDependenciesInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return
            if (symbol.callableId?.callableName?.asString() != "dependencies") return
            if (symbol.callableId?.packageName != FqName("org.gradle.kotlin.dsl")) return
        }
        // find all dependencies with their argument type in a dependencies block
        val dependencies = expression.descendantsOfType<KtCallExpression>().mapNotNull {
            val dependencyType = findDependencyType(it)
            if (dependencyType == DependencyType.SINGLE_ARGUMENT || dependencyType == DependencyType.NAMED_ARGUMENTS) it to dependencyType
            else null
        }
        // group duplicate dependencies
        val evaluator = KotlinFirConstantExpressionEvaluator()
        val dependencyGroups = dependencies.groupBy { (dependency, type) ->
            extractDependencyKey(dependency, type, evaluator)
        }.filter { it.key != null && it.value.size > 1 }

        dependencyGroups.forEach { (key, dependencies) ->
            dependencies.forEach { (dependency, _) ->
                holder.registerProblem(
                    dependency,
                    GradleInspectionBundle.message("inspection.message.avoid.duplicate.dependencies.descriptor", key)
                )
            }
        }
    }

    private fun extractDependencyKey(
        dependency: KtCallExpression,
        type: DependencyType,
        evaluator: KotlinFirConstantExpressionEvaluator
    ): String? {
        return when (type) {
            DependencyType.SINGLE_ARGUMENT -> extractSingleArgumentKey(dependency, evaluator)
            DependencyType.NAMED_ARGUMENTS -> extractNamedArgumentsKey(dependency, evaluator)
            else -> null
        }
    }

    private fun extractSingleArgumentKey(dependency: KtCallExpression, evaluator: KotlinFirConstantExpressionEvaluator): String? {
        val argumentExpression = dependency.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null

        val stringArgument = evaluator.computeConstantExpression(argumentExpression, false) as? String
        if (stringArgument != null) {
            return stringArgument
        }

        if (argumentExpression is KtCallExpression && argumentExpression.calleeExpression?.text == "kotlin") {
            val kotlinArgument = argumentExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
            return if (evaluator.computeConstantExpression(kotlinArgument, false) is String) {
                argumentExpression.text
            } else null
        }

        if (argumentExpression is KtDotQualifiedExpression) {
            val resolved = argumentExpression.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
            return if (isInVersionCatalogAccessor(resolved)) {
                argumentExpression.text
            } else null
        }

        return null
    }

    private fun extractNamedArgumentsKey(dependency: KtCallExpression, evaluator: KotlinFirConstantExpressionEvaluator): String? {
        val argList = dependency.valueArgumentList ?: return null

        val group = findNamedOrPositionalArgument(argList, "group", 0)
            ?.let { evaluator.computeConstantExpression(it, false) as? String }
            ?: return null

        val name = findNamedOrPositionalArgument(argList, "name", 1)
            ?.let { evaluator.computeConstantExpression(it, false) as? String }
            ?: return null

        val version = findNamedOrPositionalArgument(argList, "version", 2)
            ?.let { evaluator.computeConstantExpression(it, false) as? String }

        return if (version != null) "$group:$name:$version" else "$group:$name"
    }
}
