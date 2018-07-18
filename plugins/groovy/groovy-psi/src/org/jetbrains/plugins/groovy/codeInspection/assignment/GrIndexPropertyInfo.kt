// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.advancedResolve
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentTypes
import org.jetbrains.plugins.groovy.lang.psi.util.multiResolve

class GrIndexPropertyInfo(private val myCall: GrIndexProperty, private val rhs: Boolean) : CallInfo<GrIndexProperty> {

  override fun getCall(): GrIndexProperty = myCall

  override fun getElementToHighlight(): PsiElement = argumentList

  override fun getArgumentList(): GrArgumentList = myCall.argumentList

  override fun getArgumentTypes(): Array<PsiType>? = myCall.getArgumentTypes(rhs)

  override fun getInvokedExpression(): GrExpression? = myCall.invokedExpression

  override fun getQualifierInstanceType(): PsiType? = myCall.invokedExpression.type

  override fun advancedResolve(): GroovyResolveResult = myCall.advancedResolve(rhs)

  override fun multiResolve(): Array<GroovyResolveResult> = myCall.multiResolve(rhs)

  override fun getExpressionArguments(): Nothing = TODO("not supported")
  override fun getClosureArguments(): Nothing = TODO("not supported")
  override fun getNamedArguments(): Nothing = TODO("not supported")
}