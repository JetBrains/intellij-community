// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

internal fun PsiElement.isReturnTypeValueUsed() : Boolean {
  val containingBlock = parent
  return !(containingBlock is GrOpenBlock || containingBlock is GroovyFile)
}

/**
 * @return copy of the second argument
 */
internal fun deleteSecondArgument(methodCall: GrMethodCall) : PsiElement? {
  val argumentList : GrArgumentList = methodCall.argumentList
  val configurationClosure = argumentList.expressionArguments.getOrNull(1) ?: methodCall.closureArguments.singleOrNull() ?: return null
  val closureCopy = configurationClosure.copy()
  if (argumentList.expressionArguments.size == 2) {
    // second argument is in the argument list
    configurationClosure.siblings(false).find { it.text == "," }?.delete()
  }
  configurationClosure.delete()
  return closureCopy
}