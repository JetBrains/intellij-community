// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
import org.jetbrains.plugins.groovy.util.GStringConcatenationUtil

/**
 * Provides a quick fix for simplifying Gradle dependency syntax in Groovy build scripts
 * by transforming named arguments into a colon-separated string representation.
 */
class GradleDependencyNamedArgumentsFix() : PsiUpdateModCommandQuickFix() {
  override fun getName(): @IntentionName String {
    return CommonQuickFixBundle.message("fix.simplify")
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return name
  }

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val namedArguments = when (element) {
      is GrArgumentList -> element.namedArguments
      is GrListOrMap -> element.namedArguments
      else -> return
    }
    val group = namedArguments.find { it.labelName == "group" }?.expression?.text ?: return
    val name = namedArguments.find { it.labelName == "name" }?.expression?.text ?: return
    val version = namedArguments.find { it.labelName == "version" }?.expression?.text

    val factory = GroovyPsiElementFactory.getInstance(project)
    val concatExpr =
      if (version != null) factory.createExpressionFromText("$group + ':' + $name + ':' + $version", element) as GrBinaryExpression
      else factory.createExpressionFromText("$group + ':' + $name", element) as GrBinaryExpression
    val builder = StringBuilder(concatExpr.text.length)
    // assuming the named arguments resolve to strings, reuse ConvertConcatenationToGstringIntention
    GStringConcatenationUtil.convertToGString(concatExpr, builder, false)
    val newArgument = factory.createExpressionFromText(GrStringUtil.addQuotes(builder.toString(), true))
    if (newArgument is GrString) GrStringUtil.removeUnnecessaryBracesInGString(newArgument)

    when (element) {
      is GrArgumentList -> {
        namedArguments.forEach { it.delete() }
        element.add(newArgument)
      }
      is GrListOrMap -> element.replace(newArgument)
      else -> return
    }
  }
}