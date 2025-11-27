// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.toml.getResolvedDependency
import org.jetbrains.plugins.gradle.toml.getResolvedPlugin
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElementOfType

class RedundantKotlinStdLibInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {

    private val kotlinJvmPluginVersion = findKotlinJvmVersion(holder.file as KtFile)

    override fun visitCallExpression(expression: KtCallExpression) {
        if (kotlinJvmPluginVersion == null) return
        val kotlinStdLibVersion = getKotlinStdLibVersion(expression) ?: return
        if (kotlinJvmPluginVersion != kotlinStdLibVersion) return

        holder.registerProblem(
            expression,
            GradleInspectionBundle.message("inspection.message.redundant.kotlin.std.lib.dependency.descriptor"),
            RemoveDependencyFix()
        )
    }

    private fun getKotlinStdLibVersion(dependencyExpression: KtCallExpression): String? {
        val dependencyType = findDependencyType(dependencyExpression) ?: return null
        if (dependencyType == DependencyType.OTHER || !dependencyExpression.lambdaArguments.isEmpty()) return null
        if (isNonRedundantConfiguration(dependencyExpression.calleeExpression)) return null

        val argList = dependencyExpression.valueArgumentList ?: return null

        return when (dependencyType) {
            DependencyType.SINGLE_ARGUMENT -> extractVersionFromSingleArgument(argList)
            DependencyType.NAMED_ARGUMENTS -> extractVersionFromNamedArguments(argList)
            else -> null
        }
    }

    private fun isNonRedundantConfiguration(configurationExpression: KtExpression?): Boolean {
        if (configurationExpression == null) return false
        val configurationName = configurationExpression.evaluateString() ?: configurationExpression.text
        return NON_REDUNDANT_CONFIGS.any { configurationName.endsWith(it, ignoreCase = true) }
    }

    private fun extractVersionFromSingleArgument(argList: KtValueArgumentList): String? {
        val arg = argList.arguments.singleOrNull()?.getArgumentExpression() ?: return null

        // try single string case
        arg.evaluateString()?.let { stringValue ->
            return parseKotlinStdLibVersionFromString(stringValue)
        }
        // try kotlin("stdlib", version) case
        if (arg is KtCallExpression && arg.calleeExpression?.text == "kotlin") {
            return extractVersionFromKotlinCall(arg)
        }
        // try version catalog case
        if (arg is KtDotQualifiedExpression) {
            return extractVersionFromVersionCatalog(arg)
        }

        return null
    }

    private fun extractVersionFromNamedArguments(argList: KtValueArgumentList): String? {
        val args = argList.arguments
        if (args.size != 3) return null
        val argNames = args.mapNotNull { it.getArgumentName()?.asName?.identifier }.toSet()
        if (!REQUIRED_NAMED_ARGS.containsAll(argNames)) return null

        val group = findNamedOrPositionalArgument(argList, "group", 0)?.evaluateString()
            ?: return null
        val name = findNamedOrPositionalArgument(argList, "name", 1)?.evaluateString()
            ?: return null
        val version = findNamedOrPositionalArgument(argList, "version", 2)?.evaluateString()
            ?: return null

        return if (isKotlinStdLib(group, name)) version else null
    }

    private fun extractVersionFromKotlinCall(kotlinCall: KtCallExpression): String? {
        val kotlinCallArgs = kotlinCall.valueArgumentList?.arguments ?: return null
        if (kotlinCallArgs.size != 2) return null

        val kotlinId = kotlinCallArgs[0].getArgumentExpression()?.evaluateString() ?: return null
        return if (kotlinId == "stdlib") kotlinCallArgs[1].getArgumentExpression()?.evaluateString() else null
    }

    private fun extractVersionFromVersionCatalog(catalogExpression: KtDotQualifiedExpression): String? {
        val resolved = catalogExpression.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
        val (group, name, version) = getResolvedDependency(resolved, catalogExpression) ?: return null
        return if (isKotlinStdLib(group, name)) version else null
    }

    private fun parseKotlinStdLibVersionFromString(dependencyString: String): String? {
        val (group, name, version) = dependencyString.split(":").takeIf { it.size == 3 } ?: return null
        return if (isKotlinStdLib(group, name)) version else null
    }

    private fun isKotlinStdLib(group: String, name: String): Boolean {
        return group == KOTLIN_GROUP_ID && name == KOTLIN_JAVA_STDLIB_NAME
    }

    /**
     * Looks for the plugins block and returns the kotlin jvm plugin's version declared there.
     */
    private fun findKotlinJvmVersion(file: KtFile): String? {
        val pluginsBlock = file.findScriptInitializer("plugins")?.getBlock() ?: return null
        val allPlugins = PsiTreeUtil.getChildrenOfAnyType(
            pluginsBlock, KtCallExpression::class.java, KtBinaryExpression::class.java, KtDotQualifiedExpression::class.java
        )
        return allPlugins.firstNotNullOfOrNull { it.ifKotlinJvmGetVersion() }
    }

    private fun KtExpression.ifKotlinJvmGetVersion(): String? {
        val parsedCallChain = this.parsePluginCallChain() ?: return null

        if (isPluginNotApplied(parsedCallChain)) return null

        val firstCall = parsedCallChain.firstOrNull() ?: return null
        return when (firstCall.methodName) {
            "id" -> extractVersionFromIdPlugin(parsedCallChain, firstCall)
            "kotlin" -> extractVersionFromKotlinPlugin(parsedCallChain, firstCall)
            "alias" -> extractVersionFromAliasPlugin(firstCall)
            else -> null
        }
    }

    private fun isPluginNotApplied(parsedCallChain: List<ChainedMethodCallPart>): Boolean {
        return parsedCallChain.find { it.methodName == "apply" }
            ?.arguments?.singleOrNull()?.toUElementOfType<UExpression>()
            ?.evaluate() as? Boolean == false
    }

    private fun extractVersionFromIdPlugin(parsedCallChain: List<ChainedMethodCallPart>, firstCall: ChainedMethodCallPart): String? {
        if (firstCall.arguments.firstOrNull()?.evaluateString() != KOTLIN_JVM_PLUGIN) return null
        return parsedCallChain.find { it.methodName == "version" }?.arguments?.singleOrNull()?.evaluateString()
    }

    private fun extractVersionFromKotlinPlugin(parsedCallChain: List<ChainedMethodCallPart>, firstCall: ChainedMethodCallPart): String? {
        if (firstCall.arguments.firstOrNull()?.evaluateString() != "jvm") return null
        return parsedCallChain.find { it.methodName == "version" }?.arguments?.singleOrNull()?.evaluateString()
    }

    private fun extractVersionFromAliasPlugin(firstCall: ChainedMethodCallPart): String? {
        val arg = firstCall.arguments.firstOrNull() as? KtDotQualifiedExpression ?: return null
        val resolved = arg.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
        val (name, version) = getResolvedPlugin(resolved, arg) ?: return null

        return if (name == KOTLIN_JVM_PLUGIN) version else null
    }

    private data class ChainedMethodCallPart(
        val methodName: String,
        val arguments: List<KtExpression>
    )

    private fun KtExpression.parsePluginCallChain(): List<ChainedMethodCallPart>? {
        return when (this) {
            is KtBinaryExpression -> {
                val methodName = operationReference.text.trim()
                val leftCallChain = left?.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, listOf(right ?: return null))
            }

            is KtDotQualifiedExpression -> {
                val selectorExpression = selectorExpression as? KtCallExpression ?: return null
                val methodName = selectorExpression.calleeExpression?.text?.trim() ?: return null
                val arguments = selectorExpression.valueArguments.mapNotNull { it.getArgumentExpression() }
                val leftCallChain = receiverExpression.parsePluginCallChain() ?: return null
                leftCallChain + ChainedMethodCallPart(methodName, arguments)
            }

            is KtCallExpression -> {
                val methodName = (calleeExpression as? KtNameReferenceExpression)?.text?.trim() ?: return null
                val arguments = valueArguments.mapNotNull { it.getArgumentExpression() }
                listOf(ChainedMethodCallPart(methodName, arguments))
            }

            else -> null
        }
    }

    companion object {
        private const val KOTLIN_JVM_PLUGIN = "$KOTLIN_GROUP_ID.jvm"
        private val REQUIRED_NAMED_ARGS = setOf("group", "name", "version")
        private val NON_REDUNDANT_CONFIGS = setOf("compileOnly")
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
