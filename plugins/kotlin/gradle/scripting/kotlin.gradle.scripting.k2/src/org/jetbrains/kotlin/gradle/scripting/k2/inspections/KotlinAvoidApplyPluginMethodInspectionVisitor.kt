// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CORE_PLUGIN_SHORT_NAMES

class KotlinAvoidApplyPluginMethodInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {

    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val callableId = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return
            if (callableId.callableName.asString() != "apply") return
            if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return
        }

        val pluginFixInfo = if (expression.isTopLevel()) getPluginFixInfo(expression) else null
        val potentialFix = if (pluginFixInfo != null) GradleMoveApplyPluginToPluginsBlockFix(pluginFixInfo) else null

        holder.problem(
            expression,
            GradleInspectionBundle.message("inspection.message.avoid.apply.plugin.method.descriptor")
        ).maybeFix(potentialFix).register()
    }

    private fun KtExpression.isTopLevel() = this.parent is KtScriptInitializer

    /**
     * @return PluginInfo with both name, version and classpath dependency psi element if it's an external plugin,
     * PluginInfo with only name if it's a core plugin or
     * null if it's not possible to fix the issue.
     */
    private fun getPluginFixInfo(expression: KtCallExpression): PluginFixInfo? {
        val pluginName = extractPluginName(expression) ?: return null

        // no version required if it's a core plugin
        if (GRADLE_CORE_PLUGIN_SHORT_NAMES.contains(pluginName)) {
            return PluginFixInfo(pluginName, null, null)
        }

        return findExternalPluginInfo(pluginName)
    }

    private fun extractPluginName(expression: KtCallExpression): String? {
        val pluginArgument = expression.valueArguments.singleOrNull()
        // check that only the plugin id is passed as an argument
        if (pluginArgument?.getArgumentName()?.asName?.identifier != "plugin") return null
        val pluginNameExpr = pluginArgument.getArgumentExpression() ?: return null
        return pluginNameExpr.evaluateString()
    }

    private fun findExternalPluginInfo(pluginName: String): PluginFixInfo? {
        val buildScriptBlock = getBuildScriptBlock() ?: return null

        if (!isValidRepositorySetup(buildScriptBlock)) return null

        val pluginDependenciesBlock = buildScriptBlock.findBlock("dependencies") ?: return null
        return findPluginClasspathDependency(pluginDependenciesBlock, pluginName)
    }

    private fun getBuildScriptBlock(): KtBlockExpression? {
        return holder.file.asSafely<KtFile>()?.findScriptInitializer("buildscript")?.getBlock()
    }

    private fun isValidRepositorySetup(buildScriptBlock: KtBlockExpression): Boolean {
        // check that only the gradlePluginPortal() repository is used
        val repositoriesBlock = buildScriptBlock.findBlock("repositories") ?: return false
        return repositoriesBlock.children.singleOrNull()?.text == "gradlePluginPortal()"
    }

    private fun findPluginClasspathDependency(
        pluginDependenciesBlock: KtBlockExpression, pluginName: String
    ): PluginFixInfo? {
        return pluginDependenciesBlock.descendantsOfType<KtCallExpression>().firstNotNullOfOrNull { callExpression ->
            extractPluginDependencyInfo(callExpression, pluginName)
        }
    }

    private fun extractPluginDependencyInfo(
        callExpression: KtCallExpression,
        pluginName: String
    ): PluginFixInfo? {
        if (callExpression.calleeExpression?.text != "classpath") return null

        val depType = findDependencyType(callExpression) ?: return null
        val argList = callExpression.valueArgumentList ?: return null
        val args = argList.arguments

        val version = when (depType) {
            DependencyType.SINGLE_ARGUMENT -> extractVersionFromSingleArgument(args, pluginName)
            DependencyType.NAMED_ARGUMENTS -> extractVersionFromNamedArguments(argList, pluginName)
            DependencyType.OTHER -> return null
        } ?: return null

        return PluginFixInfo(pluginName, version, callExpression.createSmartPointer())
    }

    private fun extractVersionFromSingleArgument(
        args: List<KtValueArgument>,
        pluginName: String
    ): String? {
        val arg = args.firstOrNull()?.getArgumentExpression() ?: return null
        val dependencyNotation = arg.evaluateString() ?: return null
        val split = dependencyNotation.split(":")
        return if (split.size == 3 && split.first() == pluginName) split.last() else null
    }

    private fun extractVersionFromNamedArguments(
        argList: KtValueArgumentList,
        pluginName: String
    ): String? {
        val group = argList.findNamedOrPositionalArgument("group", 0)?.evaluateString() ?: return null
        if (group != pluginName) return null
        return argList.findNamedOrPositionalArgument("version", 2)?.evaluateString()
    }
}

private class GradleMoveApplyPluginToPluginsBlockFix(
    val pluginFixInfo: PluginFixInfo
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String = GradleInspectionBundle.message("intention.name.use.plugins.block")

    override fun getFamilyName(): @IntentionFamilyName String = name

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(project, true)
        val file = element.containingKtFile
        val classpathCallElement = PsiTreeUtil.findSameElementInCopy(pluginFixInfo.classpathCallElement?.element, file)

        val pluginsBlock = file.findScriptInitializer("plugins")?.getBlock()
        val pluginIdCallText = "id(\"${pluginFixInfo.name}\")${pluginFixInfo.versionString?.let { " version \"$it\"" } ?: ""}"
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
        val dependenciesBlock = classpathCallElement?.findParentBlock("dependencies") ?: return
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
    val name: String,
    val versionString: String?,
    val classpathCallElement: SmartPsiElementPointer<KtCallExpression>?
)