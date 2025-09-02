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
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.startLine
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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

private val LOG = logger<RedundantKotlinStdLibInspectionVisitor>()

class RedundantKotlinStdLibInspectionVisitor(val holder: ProblemsHolder) : KotlinGradleInspectionVisitor() {

    override fun visitCallExpression(callExpression: GrCallExpression) {
        // first check if org.jetbrains.kotlin.jvm plugin is applied before searching for kotlin-stdlib dependencies
        if (!isPluginIdOfKotlinJvm(callExpression) && !isPluginAliasOfKotlinJvm(callExpression)) return
        DependenciesVisitor(holder).visitFile(holder.file as GroovyFileBase)
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

        if (LOG.isDebugEnabled) LOG.debug(
            "Kotlin JVM plugin is applied " +
                    "at line ${callExpression.startLine(holder.file.fileDocument) + 1} " +
                    "in file ${holder.file.virtualFile.path}"
        )

        return true
    }

    private fun isPluginAliasOfKotlinJvm(callExpression: GrCallExpression): Boolean {
        val pluginAliasPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCIES_SPEC, "alias")
        )
        if (!pluginAliasPattern.accepts(callExpression) || !isPluginCallApplied(callExpression)) return false

        val reference = callExpression.expressionArguments.firstOrNull() ?: return false
        val origin = getOriginInVersionCatalog(reference) ?: return false
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

        val idRaw = pluginIdLiteral.text.cleanRawString().split(":").firstOrNull() ?: return false
        if (idRaw != KOTLIN_JVM_PLUGIN_ID) return false
        if (LOG.isDebugEnabled) LOG.debug(
            "Kotlin JVM plugin is applied " +
                    "at line ${callExpression.startLine(holder.file.fileDocument) + 1} " +
                    "in file ${holder.file.virtualFile.path} " +
                    "from version catalog ${origin.containingFile.virtualFile.path}"
        )
        return true
    }

    companion object {
        private const val KOTLIN_JVM_PLUGIN_ID = "$KOTLIN_GROUP_ID.jvm"
    }
}

private class DependenciesVisitor(val holder: ProblemsHolder) : GroovyRecursiveElementVisitor() {
    override fun visitCallExpression(callExpression: GrCallExpression) {
        val dependencyPattern = GroovyMethodCallPattern.resolvesTo(
            psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withKind(dependencyMethodKind)
        )
        if (!dependencyPattern.accepts(callExpression)) { // proceed recursively if it's not a dependency declaration
            visitElement(callExpression)
            return
        }
        if (callExpression.hasClosureArguments()) return // dependency declaration with a closure probably has a custom configuration
        val gradlePluginVersion = findResolvedKotlinGradleVersion(holder.file) ?: return
        val stdlibVersion = getResolvedLibVersion(holder.file, KOTLIN_GROUP_ID, listOf(KOTLIN_JAVA_STDLIB_NAME))
        LOG.debug("Resolved versions of Kotlin JVM Plugin: ", gradlePluginVersion, " and kotlin-stdlib: ", stdlibVersion)
        // do nothing if the stdlib version is different from the plugin version
        if (stdlibVersion != gradlePluginVersion) return

        if (callExpression.namedArguments.size >= 2 && isKotlinStdLibNamedArguments(callExpression.namedArguments.asList())) {
            registerProblem(callExpression)
            return
        }

        val singleArgument = callExpression.expressionArguments.singleOrNull()
        if (singleArgument != null) {
            if (isKotlinStdLibSingleString(singleArgument)
                || isKotlinStdLibDependencyVersionCatalog(singleArgument)) {
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
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            RemoveDependencyFix(element)
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
            ?.let { it as? GrLiteral }?.let { it.value as? String } ?: return false
        val name = namedArguments.find { it.labelName == "name" }?.expression
            ?.let { it as? GrLiteral }?.let { it.value as? String } ?: return false

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
        val origin = getOriginInVersionCatalog(expression) ?: return false
        val (dependencyGroup, dependencyName) = when (val originValue = origin.value) {
            is TomlLiteral -> originValue.text.cleanRawString().split(":").takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return false
            is TomlInlineTable -> {
                val module = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "module" }
                if (module != null) {
                    val moduleValue = module.value
                    if (moduleValue !is TomlLiteral) return false
                    moduleValue.text.cleanRawString().split(":").takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: return false
                } else {
                    val group = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "group" }
                    val name = originValue.entries.find { it.key.segments.size == 1 && it.key.segments.firstOrNull()?.name == "name" }
                    if (group == null || name == null) return false
                    val groupValue = group.value
                    val nameValue = name.value
                    if (groupValue !is TomlLiteral || nameValue !is TomlLiteral) return false
                    groupValue.text.cleanRawString() to nameValue.text.cleanRawString()
                }
            }

            else -> return false
        }

        if (LOG.isDebugEnabled) LOG.debug(
            "Found a version catalog dependency: $dependencyGroup:$dependencyName " +
                    "at line ${expression.startLine(holder.file.fileDocument) + 1} " +
                    "in file ${holder.file.virtualFile.path} " +
                    "from version catalog ${origin.containingFile.virtualFile.path}"
        )

        return dependencyGroup == KOTLIN_GROUP_ID && dependencyName == KOTLIN_JAVA_STDLIB_NAME
    }
}

private fun getOriginInVersionCatalog(expression: GrExpression): TomlKeyValue? {
    val catalogReference = expression as? GrReferenceElement<*> ?: return null
    val resolved = catalogReference.resolve()
    if (resolved !is PsiMethod || !isInVersionCatalogAccessor(resolved)) return null
    return findOriginInTomlFile(resolved, catalogReference) as? TomlKeyValue
}

private fun String.cleanRawString(): String {
    return this.removeSurrounding("\"\"\"")
        .removeSurrounding("'''")
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .replace("\r", "")
        .replace("\n", "")
}

private class RemoveDependencyFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
    override fun getText(): @IntentionName String {
        return CommonQuickFixBundle.message("fix.remove.title", "dependency")
    }

    override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        startElement.delete()
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return text
    }
}