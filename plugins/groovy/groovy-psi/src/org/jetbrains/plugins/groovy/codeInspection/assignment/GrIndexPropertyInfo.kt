/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.assignment

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.advancedResolve
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentTypes
import org.jetbrains.plugins.groovy.lang.psi.util.multiResolve

class GrIndexPropertyInfo(private val myCall: GrIndexProperty, private val rhs: Boolean) : CallInfo<GrIndexProperty> {

  override fun getCall(): GrIndexProperty = myCall

  override fun getElementToHighlight(): PsiElement = argumentList

  override fun getArgumentList() = myCall.argumentList

  override fun getArgumentTypes(): Array<PsiType>? = myCall.getArgumentTypes(rhs)

  override fun getInvokedExpression(): GrExpression? = myCall.invokedExpression

  override fun getQualifierInstanceType(): PsiType? = myCall.invokedExpression.type

  override fun advancedResolve(): GroovyResolveResult = myCall.advancedResolve(rhs)

  override fun multiResolve(): Array<GroovyResolveResult> = myCall.multiResolve(rhs)

  override fun getHighlightElementForCategoryQualifier(): PsiElement = TODO("not supported")
  override fun getExpressionArguments() = TODO("not supported")
  override fun getClosureArguments() = TODO("not supported")
  override fun getNamedArguments() = TODO("not supported")
}