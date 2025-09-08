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
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

/**
 * Provides a quick fix for simplifying Gradle dependency syntax in Groovy build scripts
 * by transforming named arguments into a colon-separated string representation.
 *
 * Assumes the named arguments are string literals.
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
    val group = namedArguments.find {
      it.labelName == "group"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"") ?: return
    val name = namedArguments.find {
      it.labelName == "name"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"") ?: return
    val version = namedArguments.find {
      it.labelName == "version"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"")
    val dependency = if (version != null) "$group:$name:$version" else "$group:$name"

    val types = namedArguments.mapNotNull { it.expression?.type }
    val newArgument = if (types.any { it.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) }) {
      // if any of the named arguments were of type GString, the new argument should also be a GString
      GroovyPsiElementFactory.getInstance(project).createExpressionFromText("\"$dependency\"")
    } else {
      // else single quoted string literal
      GroovyPsiElementFactory.getInstance(project).createLiteralFromValue(dependency)
    }

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