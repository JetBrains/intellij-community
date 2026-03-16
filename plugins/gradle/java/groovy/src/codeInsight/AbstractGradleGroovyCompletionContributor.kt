// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import kotlin.text.plus

/**
 * @author Vladislav.Soroka
 */
abstract class AbstractGradleGroovyCompletionContributor : CompletionContributor() {

  protected fun findNamedArgumentValue(namedArgumentsOwner: GrNamedArgumentsOwner?, label: String): String? {
    val namedArgument = namedArgumentsOwner?.findNamedArgument(label) ?: return null
    return (namedArgument.expression as? GrLiteralImpl)?.value?.toString()
  }

  companion object {
    val GRADLE_FILE_PATTERN: ElementPattern<PsiElement> = PlatformPatterns.psiElement()
      .inFile(PlatformPatterns.psiFile().withName(StandardPatterns.string().endsWith('.' + GradleConstants.EXTENSION)))
  }
}