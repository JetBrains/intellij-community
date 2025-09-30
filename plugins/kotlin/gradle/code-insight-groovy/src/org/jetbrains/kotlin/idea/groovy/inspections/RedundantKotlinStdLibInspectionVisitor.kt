// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.startLine
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.findResolvedKotlinJvmVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.isKotlinStdLibDependency
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native.KotlinGradleCodeInsightCommonBundle
import org.jetbrains.kotlin.idea.groovy.inspections.DifferentStdlibGradleVersionInspection.Companion.getResolvedLibVersion
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor.Companion.dependencyMethodKind
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator

private val LOG = logger<RedundantKotlinStdLibInspectionVisitor>()

class RedundantKotlinStdLibInspectionVisitor(private val holder: ProblemsHolder) : KotlinGradleInspectionVisitor() {
    override fun visitCallExpression(callExpression: GrCallExpression) {
        if (!apiDependencyPattern.accepts(callExpression)) return
        if (callExpression.hasClosureArguments()) return // dependency declaration with a closure probably has a custom configuration

        val kotlinJvmPluginVersion = findResolvedKotlinJvmVersion(holder.file)
        val kotlinStdLibVersion = getResolvedLibVersion(holder.file, KOTLIN_GROUP_ID, listOf(KOTLIN_JAVA_STDLIB_NAME))
        if (kotlinJvmPluginVersion == null || kotlinStdLibVersion == null || kotlinJvmPluginVersion != kotlinStdLibVersion) return

        if (callExpression.namedArguments.size >= 2 && isKotlinStdLibNamedArguments(callExpression.namedArguments.asList())) {
            registerProblem(callExpression)
            return
        }

        val singleArgument = callExpression.expressionArguments.singleOrNull()
        if (singleArgument != null) {
            if (isKotlinStdLibSingleString(singleArgument) || isKotlinStdLibDependencyVersionCatalog(singleArgument)) {
                registerProblem(callExpression)
            }
            return
        }

        for (argument in callExpression.expressionArguments) {
            val type = argument.type ?: continue
            when {
                InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_CHAR_SEQUENCE)
                        || type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) -> {
                    if (isKotlinStdLibSingleString(argument)) registerProblem(argument)
                }

                InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) -> {
                    if (argument is GrListOrMap && isKotlinStdLibNamedArguments(argument.namedArguments.asList())) {
                        registerProblem(argument)
                    }
                }
            }
        }
    }

    private fun registerProblem(element: PsiElement) {
        holder.registerProblem(
            element,
            KotlinGradleCodeInsightCommonBundle.message("inspection.message.redundant.kotlin.std.lib.dependency.descriptor"),
            RemoveDependencyFix()
        )
    }

    private fun isKotlinStdLibNamedArguments(namedArguments: List<GrNamedArgument>): Boolean {
        // check that the dependency does not contain anything extra besides the group and name (and version)
        val namedArgumentsNames = namedArguments.map { it.labelName }
        when (namedArgumentsNames.size) {
            2 -> if (!namedArgumentsNames.containsAll(setOf("group", "name"))) return false
            3 -> if (!namedArgumentsNames.containsAll(setOf("group", "name", "version"))) return false
            else -> return false
        }

        val group = namedArguments.find { it.labelName == "group" }?.expression
            ?.let { GroovyConstantExpressionEvaluator.evaluate(it) as? String } ?: return false
        val name = namedArguments.find { it.labelName == "name" }?.expression
            ?.let { GroovyConstantExpressionEvaluator.evaluate(it) as? String } ?: return false

        if (LOG.isDebugEnabled) LOG.debug(
            "Found a map notation dependency: $group:$name " +
                    "at line ${namedArguments[0].startLine(holder.file.fileDocument) + 1} " +
                    "in file ${holder.file.virtualFile.path}"
        )

        return group == KOTLIN_GROUP_ID && name == KOTLIN_JAVA_STDLIB_NAME
    }

    private fun isKotlinStdLibSingleString(argument: GrExpression): Boolean {
        val string = GroovyConstantExpressionEvaluator.evaluate(argument) as? String ?: return false
        val (group, name) = string.split(":").takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return false

        if (LOG.isDebugEnabled) LOG.debug(
            "Found a single string notation dependency: $group:$name " +
                    "at line ${argument.startLine(holder.file.fileDocument) + 1} " +
                    "in file ${holder.file.virtualFile.path}"
        )

        return group == KOTLIN_GROUP_ID && name == KOTLIN_JAVA_STDLIB_NAME
    }

    private fun isKotlinStdLibDependencyVersionCatalog(expression: GrExpression): Boolean {
        val catalogReference = expression as? GrReferenceElement<*> ?: return false
        val resolved = catalogReference.resolve() as? PsiMethod ?: return false
        return isKotlinStdLibDependency(resolved, expression)
    }

    companion object {
        private val apiDependencyPattern = GroovyMethodCallPattern
            .resolvesTo(psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withKind(dependencyMethodKind))
    }
}

private class RemoveDependencyFix() : PsiUpdateModCommandQuickFix() {
    override fun getName(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.remove.title", "dependency")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        element.delete()
    }
}
