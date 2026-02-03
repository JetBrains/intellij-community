// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
import org.jetbrains.plugins.groovy.util.GStringConcatenationUtil

/**
 * Provides a quick fix for simplifying Gradle dependency syntax in Groovy build scripts
 * by transforming named arguments into a colon-separated string representation.
 */
class GradleDependencyNamedArgumentsFix(
  private val concat: String,
  private val targetConfig: String?,
) : PsiUpdateModCommandQuickFix() {

  override fun getName(): @IntentionName String {
    return CommonQuickFixBundle.message("fix.simplify")
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return name
  }

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val argList = when (element) {
      is GrMethodCall -> {
        element.argumentList
      }
      is GrListOrMap -> element
      else -> return
    }

    val factory = GroovyPsiElementFactory.getInstance(project)
    val concatExpr = factory.createExpressionFromText(concat, argList) as GrBinaryExpression
    val builder = StringBuilder(concatExpr.text.length)
    // assuming the named arguments resolve to strings, reuse ConvertConcatenationToGstringIntention
    GStringConcatenationUtil.convertToGString(concatExpr, builder, false)
    val newArgument = factory.createExpressionFromText(GrStringUtil.addQuotes(builder.toString(), true))
    if (newArgument is GrString) GrStringUtil.removeUnnecessaryBracesInGString(newArgument)

    if (targetConfig == null) {
      replaceArguments(argList, newArgument)
      return
    }

    if (argList !is GrArgumentList) return

    val methodCall = element as GrMethodCall
    val configBlock = methodCall.closureArguments.singleOrNull()
    val targetConfigExpr = factory.createExpressionFromText("targetConfiguration = $targetConfig")

    if (configBlock != null) {
      configBlock.addStatementBefore(targetConfigExpr, configBlock.statements.firstOrNull())
      replaceArguments(argList, newArgument)
    }
    else {
      val newElement = factory.createExpressionFromText(
        "${methodCall.invokedExpression.text}(${newArgument.text}) {}"
      ) as GrMethodCall
      newElement.closureArguments.singleOrNull()!!.addStatementBefore(targetConfigExpr, null)
      methodCall.replace(newElement)
    }
  }

  private fun replaceArguments(argList: PsiElement, newArgument: GrExpression) {
    when (argList) {
      is GrArgumentList -> {
        argList.namedArguments.forEach { it.delete() }
        argList.add(newArgument)
      }
      is GrListOrMap -> argList.replace(newArgument)
      else -> return
    }
  }

  companion object {

    fun createFixIfPossible(argList: GroovyPsiElement): GradleDependencyNamedArgumentsFix? {
      val namedArguments = when (argList) {
        is GrArgumentList -> argList.namedArguments
        is GrListOrMap -> argList.namedArguments
        else -> return null
      }
      val group = namedArguments.find { it.labelName == "group" }?.expression?.text ?: return null
      val name = namedArguments.find { it.labelName == "name" }?.expression?.text ?: return null

      val targetConfig = namedArguments.find { it.labelName == "configuration" }?.expression?.text
      if (targetConfig != null && argList !is GrArgumentList) return null // would need to add a config block to a list of dependencies

      val version = namedArguments.find { it.labelName == "version" }?.expression?.text
      val classifier = namedArguments.find { it.labelName == "classifier" }?.expression?.text
      val ext = namedArguments.find { it.labelName == "ext" }?.expression?.text

      val concat = buildSingleStringDependencyNotation(group, name, version, classifier, ext) ?: return null
      return GradleDependencyNamedArgumentsFix(concat, targetConfig)
    }

    @ApiStatus.Internal
    fun buildSingleStringDependencyNotation(
      group: String,
      name: String,
      version: String?,
      classifier: String?,
      ext: String?,
    ): String? {
      val base = "$group + \":\" + $name"

      if (version == null) {
        return if (classifier == null && ext == null) base else null
      }

      val withVersion = "$base + \":\" + $version"

      return when {
        classifier != null && ext != null -> "$withVersion + \":\" + $classifier + \"@\" + $ext"
        classifier != null -> "$withVersion + \":\" + $classifier"
        ext != null -> "$withVersion + \"@\" + $ext"
        else -> withVersion
      }
    }
  }
}