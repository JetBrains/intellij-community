// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

/**
 * Provides a quick fix for simplifying Gradle dependency syntax in Groovy build scripts
 * by transforming named arguments into a colon-separated string representation.
 *
 * Assumes the named arguments are string literals.
 *
 * @param element The dependency GrMethodCall PSI element.
 */
class GradleDependencyNamedArgumentsFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
  override fun getText(): @IntentionName String {
    return CommonQuickFixBundle.message("fix.simplify")
  }

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val namedArguments = when (startElement) {
      is GrMethodCall -> startElement.namedArguments
      is GrListOrMap -> startElement.namedArguments
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

    when (startElement) {
      is GrMethodCall -> {
        namedArguments.forEach { it.delete() }
        startElement.argumentList.add(newArgument)
      }
      is GrListOrMap -> startElement.replace(newArgument)
      else -> return
    }
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return text
  }
}