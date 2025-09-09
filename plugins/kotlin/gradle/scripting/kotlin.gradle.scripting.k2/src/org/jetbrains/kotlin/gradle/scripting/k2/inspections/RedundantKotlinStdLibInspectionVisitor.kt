// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.findResolvedKotlinJvmVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.getResolvedLibVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.isKotlinStdLibDependency
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native.KotlinGradleCodeInsightCommonBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinFirConstantExpressionEvaluator
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME

class RedundantKotlinStdLibInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        val dependencyType = findDependencyType(expression) ?: return
        if (dependencyType == DependencyType.OTHER) return

        val kotlinJvmPluginVersion = findResolvedKotlinJvmVersion(holder.file)
        val kotlinStdLibVersion = getResolvedLibVersion(holder.file, KOTLIN_GROUP_ID, listOf(KOTLIN_JAVA_STDLIB_NAME))
        if (kotlinJvmPluginVersion == null || kotlinStdLibVersion == null || kotlinJvmPluginVersion != kotlinStdLibVersion) return

        if (!expression.lambdaArguments.isEmpty()) return
        val argList = expression.valueArgumentList ?: return
        if (!argList.isPhysical) return

        val args = argList.arguments

        when (dependencyType) {
            DependencyType.SINGLE_ARGUMENT -> {
                val arg = args.singleOrNull()?.getArgumentExpression() ?: return
                val string = KotlinFirConstantExpressionEvaluator().computeConstantExpression(arg, false) as? String
                if (string != null) {
                    if (string.startsWith("$KOTLIN_GROUP_ID:$KOTLIN_JAVA_STDLIB_NAME")) registerProblem(expression)
                } else if (arg is KtCallExpression && arg.calleeExpression?.text == "kotlin") {
                    val kotlinId = arg.valueArgumentList?.arguments?.firstOrNull()?.getArgumentExpression()
                        ?.let { KotlinFirConstantExpressionEvaluator().computeConstantExpression(it, false) as? String }
                    if (kotlinId == "stdlib") registerProblem(expression)
                } else if (arg is KtDotQualifiedExpression) {
                    val resolved = arg.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return
                    if (isKotlinStdLibDependency(resolved, expression)) registerProblem(expression)
                }
            }

            DependencyType.NAMED_ARGUMENTS -> {
                // check that there are only 2-3 arguments and the named ones are among group, name or version
                if (args.size !in 2..3) return
                val ids = args.mapNotNull { it.getArgumentName()?.asName?.identifier }
                if (!setOf("group", "name", "version").containsAll(ids)) return

                val group = findNamedOrPositionalArgument(argList, "group", 0)
                    ?.let { KotlinFirConstantExpressionEvaluator().computeConstantExpression(it, false) as? String }
                    ?: return
                val name = findNamedOrPositionalArgument(argList, "name", 1)
                    ?.let { KotlinFirConstantExpressionEvaluator().computeConstantExpression(it, false) as? String }
                    ?: return

                if (group == KOTLIN_GROUP_ID && name == KOTLIN_JAVA_STDLIB_NAME) registerProblem(expression)
            }

            else -> return
        }
    }

    private fun registerProblem(element: KtCallExpression) {
        holder.registerProblem(
            element,
            KotlinGradleCodeInsightCommonBundle.message("inspection.message.redundant.kotlin.std.lib.dependency.descriptor"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            RemoveDependencyFix()
        )
    }
}

private class RemoveDependencyFix() : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.remove.title", "dependency")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        element.delete()
    }
}
