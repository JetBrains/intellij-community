// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native.KotlinGradleCodeInsightCommonBundle
import org.jetbrains.kotlin.idea.groovy.inspections.DifferentStdlibGradleVersionInspection.Companion.getResolvedLibVersion
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor.Companion.dependencyMethodKind
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

private val LOG = logger<RedundantKotlinStdLibInspectionVisitor>()

class RedundantKotlinStdLibInspectionVisitor(val holder: ProblemsHolder) : KotlinGradleInspectionVisitor() {

    private val KOTLIN_JVM_PLUGIN_ID = "$KOTLIN_GROUP_ID.jvm"

    override fun visitCallExpression(callExpression: GrCallExpression) {
        // first check if org.jetbrains.kotlin.jvm plugin is applied before searching for kotlin-stdlib dependencies
        if (!isPluginIdOfKotlinJvm(callExpression) && !isPluginAliasOfKotlinJvm(callExpression)) return
        DependenciesVisitor(holder).visitFile(callExpression.containingFile as GroovyFileBase)
    }

    private fun isPluginCallApplied(callExpression: GrCallExpression): Boolean {
        val applyPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCY_SPEC, "apply")
        )
        val applyCall = callExpression.getParentOfTypesAndPredicate(false, GrMethodCall::class.java) {
            applyPattern.accepts(it)
        }

        // no implicit kotlin-stdlib dependency if the kotlin plugin is not applied
        return !(applyCall != null && applyCall.expressionArguments.firstOrNull().booleanValue() == false)
    }

    private fun isPluginIdOfKotlinJvm(callExpression: GrCallExpression): Boolean {
        val pluginIdPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCIES_SPEC, "id")
        )
        if (!pluginIdPattern.accepts(callExpression) || !isPluginCallApplied(callExpression)) return false

        val pluginId = callExpression.expressionArguments.firstOrNull()
        // TODO only works if plugin id argument is a literal
        if (pluginId !is GrLiteral || pluginId.value != KOTLIN_JVM_PLUGIN_ID) return false

        LOG.debug("Kotlin JVM plugin is applied in file ${callExpression.containingFile.virtualFile.path}")
        return true
    }

    private fun isPluginAliasOfKotlinJvm(callExpression: GrCallExpression): Boolean {
        val pluginAliasPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCIES_SPEC, "alias")
        )
        if (!pluginAliasPattern.accepts(callExpression) || !isPluginCallApplied(callExpression)) return false

        val catalogReference = callExpression.expressionArguments.firstOrNull() as? GrReferenceElement<*> ?: return false
        val resolved = catalogReference.resolve()
        if (resolved !is PsiMethod || !isInVersionCatalogAccessor(resolved)) return false
        val origin = findOriginInTomlFile(resolved, catalogReference) as? TomlKeyValue ?: return false

        val pluginIdLiteral = when (val originValue = origin.value) {
            is TomlLiteral -> originValue
            is TomlInlineTable -> {
                val pluginId = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "id" }?.value
                    ?: return false
                if (pluginId !is TomlLiteral) return false
                pluginId
            }

            else -> return false
        }

        val idRaw = pluginIdLiteral.text.removeSurrounding("\"").removeSurrounding("'")
            .split(":").firstOrNull() ?: return false
        if (idRaw != KOTLIN_JVM_PLUGIN_ID) return false
        LOG.debug(
            """
            Kotlin JVM plugin is applied in file ${callExpression.containingFile.virtualFile.path} 
            from version catalog ${origin.containingFile.virtualFile.path}
            """
        )
        return true
    }
}

private class DependenciesVisitor(val holder: ProblemsHolder) : GroovyRecursiveElementVisitor() {
    override fun visitCallExpression(callExpression: GrCallExpression) {
        val dependencyPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withKind(dependencyMethodKind)
        )
        if (!dependencyPattern.accepts(callExpression)) {
            // proceed recursively if it's not a dependency declaration
            visitElement(callExpression)
            return
        }

        val callExpressionText = callExpression.text
        val index = callExpressionText.indexOf(KOTLIN_JAVA_STDLIB_NAME)
        if (index == -1
            || !callExpressionText.contains(KOTLIN_GROUP_ID)
            // This prevents detecting kotlin-stdlib inside kotlin-stdlib-common, -jdk8, etc.
            || callExpressionText.getOrNull(index + KOTLIN_JAVA_STDLIB_NAME.length) == '-'
        ) return
        val gradlePluginVersion = findResolvedKotlinGradleVersion(callExpression.containingFile) ?: return
        val stdlibVersion = getResolvedLibVersion(callExpression.containingFile, KOTLIN_GROUP_ID, listOf(KOTLIN_JAVA_STDLIB_NAME))
        LOG.debug("Kotlin Plugin Version: $gradlePluginVersion, kotlin-stdlib Version: $stdlibVersion")

        // do nothing if the stdlib version is different from the plugin version
        if (stdlibVersion != gradlePluginVersion) return
        holder.registerProblem(
            callExpression,
            KotlinGradleCodeInsightCommonBundle.message("inspection.message.redundant.kotlin.std.lib.dependency.descriptor"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            RemoveDependencyFix(callExpression)
        )
    }
}

private class RemoveDependencyFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
    override fun getText(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.remove.title", "dependency")
    }

    override fun invoke(
        project: Project,
        psiFile: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        startElement.delete()
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return text
    }
}