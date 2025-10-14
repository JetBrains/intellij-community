// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.childrenOfType
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor.Companion.dependencyMethodKind
import org.jetbrains.plugins.gradle.toml.getResolvedDependency
import org.jetbrains.plugins.gradle.toml.getResolvedPlugin
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType

private val LOG = logger<GroovyRedundantKotlinStdLibInspectionVisitor>()

class GroovyRedundantKotlinStdLibInspectionVisitor(private val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitCallExpression(callExpression: GrCallExpression) {
    if (!apiDependencyPattern.accepts(callExpression)) return
    if (callExpression.hasClosureArguments()) return // dependency declaration with a closure probably has a custom configuration

    // named arguments case
    if (callExpression.namedArguments.size == 3) {
      val kotlinStdLibVersion = getVersionIfKotlinStdLibNamedArguments(callExpression.namedArguments.asList()) ?: return
      val kotlinJvmPluginVersion = findKotlinJvmVersion(holder.file as GroovyFile) ?: return
      if (kotlinStdLibVersion == kotlinJvmPluginVersion) registerProblem(callExpression)
      return
    }

    // single argument case (string or version catalog)
    val singleArgument = callExpression.expressionArguments.singleOrNull()
    if (singleArgument != null) {
      val kotlinStdLibVersion = getVersionIfKotlinStdLibSingleString(singleArgument)
                                ?: getVersionIfKotlinStdLibVersionCatalog(singleArgument)
                                ?: return
      val kotlinJvmPluginVersion = findKotlinJvmVersion(holder.file as GroovyFile) ?: return
      if (kotlinStdLibVersion == kotlinJvmPluginVersion) registerProblem(callExpression)
      return
    }

    // list of arguments case
    // delay finding the kotlin jvm plugin version
    var kotlinJvmPluginVersion: String? = null

    for (argument in callExpression.expressionArguments) {
      if (argument is GrListOrMap && argument.isMap) {
        val kotlinStdLibVersion = getVersionIfKotlinStdLibNamedArguments(argument.namedArguments.asList())
                                  ?: continue

        if (kotlinJvmPluginVersion == null) kotlinJvmPluginVersion = findKotlinJvmVersion(holder.file as GroovyFile)
        if (kotlinStdLibVersion == kotlinJvmPluginVersion) registerProblem(argument)
      }
      else {
        val unwrappedArgument = if (argument is GrListOrMap) argument.initializers.singleOrNull() ?: continue else argument
        val kotlinStdLibVersion = getVersionIfKotlinStdLibSingleString(unwrappedArgument)
                                  ?: getVersionIfKotlinStdLibVersionCatalog(unwrappedArgument)
                                  ?: continue

        if (kotlinJvmPluginVersion == null) kotlinJvmPluginVersion = findKotlinJvmVersion(holder.file as GroovyFile)
        if (kotlinStdLibVersion == kotlinJvmPluginVersion) registerProblem(argument)
      }
    }
  }

  private fun registerProblem(element: PsiElement) {
    holder.registerProblem(
      element,
      GradleInspectionBundle.message("inspection.message.redundant.kotlin.std.lib.dependency.descriptor"),
      RemoveDependencyFix()
    )
  }

  private fun getVersionIfKotlinStdLibNamedArguments(namedArguments: List<GrNamedArgument>): String? {
    val namedArgumentsNames = namedArguments.map { it.labelName }.toSet()
    if (REQUIRED_NAMED_ARGS != namedArgumentsNames) return null

    val group = namedArguments.find { it.labelName == "group" }?.expression
                  .toUElementOfType<UExpression>()?.evaluateString() ?: return null
    val name = namedArguments.find { it.labelName == "name" }?.expression
                 .toUElementOfType<UExpression>()?.evaluateString() ?: return null
    val version = namedArguments.find { it.labelName == "version" }?.expression
                    .toUElementOfType<UExpression>()?.evaluateString() ?: return null

    LOG.debug {
      "Found a map notation kotlin-stdlib dependency: $group:$name:$version " +
      "at line ${holder.file.fileDocument.getLineNumber(namedArguments[0].textRange.startOffset) + 1} " +
      "in file ${holder.file.virtualFile.path}"
    }

    return if (isKotlinStdLib(group, name)) version else null
  }

  private fun getVersionIfKotlinStdLibSingleString(argument: GrExpression): String? {
    val stringValue = argument.toUElementOfType<UExpression>()?.evaluateString() ?: return null
    val version = parseKotlinStdLibVersionFromString(stringValue) ?: return null

    LOG.debug {
      "Found a single string notation kotlin-stdlib dependency: $stringValue " +
      "at line ${holder.file.fileDocument.getLineNumber(argument.textRange.startOffset) + 1} " +
      "in file ${holder.file.virtualFile.path}"
    }

    return version
  }

  private fun getVersionIfKotlinStdLibVersionCatalog(argument: GrExpression): String? {
    val catalogReference = argument as? GrReferenceElement<*> ?: return null
    val resolved = catalogReference.resolve() as? PsiMethod ?: return null
    val stringValue = getResolvedDependency(resolved, argument) ?: return null
    val version = parseKotlinStdLibVersionFromString(stringValue) ?: return null

    LOG.debug {
      "Found a version catalog notation kotlin-stdlib dependency: $stringValue " +
      "at line ${holder.file.fileDocument.getLineNumber(argument.textRange.startOffset) + 1} " +
      "in file ${holder.file.virtualFile.path}"
    }

    return version
  }

  private fun parseKotlinStdLibVersionFromString(dependencyString: String): String? {
    val (group, name, version) = dependencyString.split(":").takeIf { it.size == 3 } ?: return null
    return if (isKotlinStdLib(group, name)) version else null
  }

  private fun isKotlinStdLib(group: String, name: String): Boolean {
    return group == KOTLIN_GROUP_ID && name == KOTLIN_JAVA_STDLIB_NAME
  }

  private fun findKotlinJvmVersion(file: GroovyFile): String? {
    val pluginsBlock = file.childrenOfType<GrMethodCallExpression>()
                         .filter { it.closureArguments.isNotEmpty() }
                         .find { it.invokedExpression.text == "plugins" }
                         ?.closureArguments?.firstOrNull() ?: return null

    // go through the plugins block and find the kotlin-jvm plugin
    return pluginsBlock.childrenOfType<GrMethodCall>().firstNotNullOfOrNull { it.ifKotlinJvmGetVersion() }
  }

  private fun GrMethodCall.ifKotlinJvmGetVersion(): String? {
    val parsedCallChain = this.parsePluginCallChain() ?: return null

    if (isPluginNotApplied(parsedCallChain)) return null

    val firstCall = parsedCallChain.firstOrNull() ?: return null
    return when (firstCall.methodName) {
      "id" -> extractVersionFromIdPlugin(parsedCallChain, firstCall)
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
    if (firstCall.arguments.firstOrNull().toUElementOfType<UExpression>()?.evaluateString() != KOTLIN_JVM_PLUGIN) return null
    return parsedCallChain.find { it.methodName == "version" }
      ?.arguments?.singleOrNull()?.toUElementOfType<UExpression>()
      ?.evaluateString()
  }

  private fun extractVersionFromAliasPlugin(firstCall: ChainedMethodCallPart): String? {
    val catalogReference = firstCall.arguments.firstOrNull() as? GrReferenceElement<*> ?: return null
    val resolved = catalogReference.resolve() as? PsiMethod ?: return null
    val (name, version) = getResolvedPlugin(resolved, catalogReference)
                            ?.split(":")?.takeIf { it.size == 2 } ?: return null

    return if (name == KOTLIN_JVM_PLUGIN) version else null
  }

  class ChainedMethodCallPart(
    val methodName: String,
    val arguments: List<GrExpression>,
  )

  private fun GrMethodCall.parsePluginCallChain(): List<ChainedMethodCallPart>? {
    val outerInvokedExpression = invokedExpression as? GrReferenceExpression ?: return null
    val methodName = outerInvokedExpression.referenceName ?: return null

    val arguments = expressionArguments.toList()
    val innerExpression = outerInvokedExpression.qualifierExpression
    if (innerExpression == null) return listOf(ChainedMethodCallPart(methodName, arguments))

    if (innerExpression !is GrMethodCall) return null
    val innerChainParts = innerExpression.parsePluginCallChain() ?: return null
    return innerChainParts + ChainedMethodCallPart(methodName, arguments)
  }

  companion object {
    private val apiDependencyPattern = GroovyMethodCallPattern
      .resolvesTo(psiMethod(GRADLE_API_DEPENDENCY_HANDLER).withKind(dependencyMethodKind))
    private const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"
    private const val KOTLIN_JAVA_STDLIB_NAME = "kotlin-stdlib"
    private const val KOTLIN_JVM_PLUGIN = "$KOTLIN_GROUP_ID.jvm"
    private val REQUIRED_NAMED_ARGS = setOf("group", "name", "version")
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
