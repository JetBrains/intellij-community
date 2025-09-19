// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.findParentOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinFirConstantExpressionEvaluator
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CORE_PLUGIN_SHORT_NAMES

class KotlinAvoidApplyPluginMethodInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    private val constEvaluator = KotlinFirConstantExpressionEvaluator()

    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return
            if (symbol.callableId?.callableName?.asString() != "apply") return
            if (symbol.callableId?.packageName != FqName("org.gradle.kotlin.dsl")) return
        }

        val pluginFixInfo = canBeFixed(expression)

        if (pluginFixInfo != null) {
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.apply.plugin.method.descriptor"),
                GradleMoveApplyPluginToPluginsBlockFix(pluginFixInfo)
            )
        } else {
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.apply.plugin.method.descriptor")
            )
        }
    }

    /**
     * @return PluginInfo with both name, version and classpath dependency psi element if it's an external plugin,
     * PluginInfo with only name if it's a core plugin or
     * null if it's not possible to fix the issue.
     */
    private fun canBeFixed(expression: KtCallExpression): PluginFixInfo? {
        val pluginArgument = expression.valueArguments.singleOrNull()

        // check that only the plugin id is passed as an argument
        if (pluginArgument?.getArgumentName()?.asName?.identifier != "plugin") return null

        val pluginNameExpr = pluginArgument.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        val pluginName = constEvaluator.computeConstantExpression(pluginNameExpr, false) as? String ?: return null

        // no version required if it's a core plugin
        if (GRADLE_CORE_PLUGIN_SHORT_NAMES.contains(pluginName)) return PluginFixInfo(pluginNameExpr.text, null, null)

        val buildScriptBlock = holder.file.asSafely<KtFile>()?.findScriptInitializer("buildscript")?.getBlock() ?: return null

        // check that only the gradlePluginPortal() repository is used
        val repositoriesBlock = buildScriptBlock.findBlock("repositories") ?: return null
        if (repositoriesBlock.children.singleOrNull()?.text != "gradlePluginPortal()") return null

        // find the plugin's corresponding dependency
        val pluginDependenciesBlock = buildScriptBlock.findBlock("dependencies") ?: return null
        val (psiElement, version) = findPluginClasspathDependency(pluginDependenciesBlock, pluginName) ?: return null

        return PluginFixInfo(pluginNameExpr.text, version, psiElement)
    }

    private fun findPluginClasspathDependency(
        pluginDependenciesBlock: KtBlockExpression, pluginName: String
    ): Pair<SmartPsiElementPointer<KtCallExpression>, String>? =
        pluginDependenciesBlock.descendantsOfType<KtCallExpression>().firstNotNullOfOrNull { callExpression ->
            if (callExpression.calleeExpression?.text != "classpath") return@firstNotNullOfOrNull null
            val depType = findDependencyType(callExpression) ?: return@firstNotNullOfOrNull null
            val argList = callExpression.valueArgumentList ?: return@firstNotNullOfOrNull null
            val args = argList.arguments

            val version = when (depType) {
                DependencyType.SINGLE_ARGUMENT -> {
                    val arg = args.firstOrNull()?.getArgumentExpression() ?: return@firstNotNullOfOrNull null
                    val classpath = constEvaluator.computeConstantExpression(arg, false) as? String
                        ?: return@firstNotNullOfOrNull null
                    val split = classpath.split(":")
                    if (split.size == 3 && split.first() == pluginName) split.last()
                    else return@firstNotNullOfOrNull null
                }

                DependencyType.NAMED_ARGUMENTS -> {
                    val group = findNamedOrPositionalArgument(argList, "group", 0)
                        ?.let { constEvaluator.computeConstantExpression(it, false) as? String }
                        ?: return@firstNotNullOfOrNull null
                    if (group != pluginName) return@firstNotNullOfOrNull null
                    findNamedOrPositionalArgument(argList, "version", 2)
                        ?.let { constEvaluator.computeConstantExpression(it, false) as? String }
                        ?: return@firstNotNullOfOrNull null
                }

                DependencyType.OTHER -> return@firstNotNullOfOrNull null
            }

            callExpression.createSmartPointer() to version
        }

}

private class GradleMoveApplyPluginToPluginsBlockFix(
    val pluginFixInfo: PluginFixInfo
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String {
        return GradleInspectionBundle.message("intention.name.move.apply.to.plugins.block")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(project, true)
        val file = element.containingKtFile
        val pluginsBlock = file.findScriptInitializer("plugins")?.getBlock()
        val pluginIdCallText = "id(${pluginFixInfo.nameText})${pluginFixInfo.versionString?.let { " version \"$it\"" } ?: ""}"
        // add the plugin to the plugins block or create the block if it's missing with the plugin declaration
        if (pluginsBlock != null) {
            pluginsBlock.add(psiFactory.createNewLine(1))
            pluginsBlock.add(psiFactory.createExpression(pluginIdCallText))
        } else {
            file.script!!.blockExpression.let {
                it.addBefore(psiFactory.createNewLine(2), it.firstChild)
                it.addBefore(psiFactory.createExpression("plugins {\n$pluginIdCallText\n}"), it.firstChild)
            }
        }

        // delete the apply call
        element.delete()

        // buildscript block handling
        val originalClasspathCallElement = pluginFixInfo.classpathCallElement?.element ?: return
        val classpathCallElement = PsiTreeUtil.findSameElementInCopy(originalClasspathCallElement, file)
        val dependenciesBlock = classpathCallElement.findParentBlock("dependencies") ?: return
        // delete the corresponding plugin's dependency declaration
        classpathCallElement.delete()
        if (!dependenciesBlock.children.isEmpty()) return
        // if the dependencies block is empty, delete it as well
        val buildscriptBlock = dependenciesBlock.findParentBlock("buildscript") ?: return
        dependenciesBlock.findParentOfType<KtCallExpression>()?.delete()
        // if the buildscript block only has one child (the repositories block), delete it as well
        if (buildscriptBlock.children.size == 1) buildscriptBlock.findParentOfType<KtCallExpression>()?.delete()
    }
}

private data class PluginFixInfo(
    val nameText: String,
    val versionString: String?,
    val classpathCallElement: SmartPsiElementPointer<KtCallExpression>?
)