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
    if (startElement !is GrMethodCall) return
    val group = startElement.namedArguments.find {
      it.labelName == "group"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"") ?: return
    val name = startElement.namedArguments.find {
      it.labelName == "name"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"") ?: return
    val version = startElement.namedArguments.find {
      it.labelName == "version"
    }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"")
    val dependency = if (version != null) "$group:$name:$version" else "$group:$name"

    val types = startElement.namedArguments.mapNotNull { it.expression?.type }

    startElement.namedArguments.forEach { it.delete() }

    // add dependency id as one string argument
    if (types.any { it.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) }) {
      // if any of the named arguments were of type GString, the new argument should also be a GString
      startElement.argumentList.add(GroovyPsiElementFactory.getInstance(project).createExpressionFromText("\"$dependency\""))
    } else {
      // else single quoted string literal
      startElement.argumentList.add(GroovyPsiElementFactory.getInstance(project).createLiteralFromValue(dependency))
    }
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return text
  }
}