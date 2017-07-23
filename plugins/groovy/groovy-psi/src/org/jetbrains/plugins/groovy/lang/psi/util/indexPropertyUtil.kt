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
@file:JvmName("GroovyIndexPropertyUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.impl.GrImmediateTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.getClassReferenceFromExpression

fun GrIndexProperty.isSimpleArrayAccess(): Boolean {
  return getSimpleArrayAccessType() != null
}

fun GrIndexProperty.getSimpleArrayAccessType(): PsiType? {
  val thisType = invokedExpression.type as? PsiArrayType ?: return null
  val argument = argumentList.allArguments.singleOrNull() as? GrExpression ?: return null
  if (TypesUtil.isAssignableByMethodCallConversion(PsiType.INT, argument.type, this) || argument.isSingleCharLiteral()) {
    return thisType.componentType
  }
  else {
    return null
  }
}

private fun GrExpression.isSingleCharLiteral(): Boolean {
  if (this !is GrLiteral) return false
  val value = this.value as? String ?: return false
  return value.length == 1
}

/**
 * Examples: `int[]`, `double[][][]`, `String[]`.
 */
fun GrIndexProperty.isClassLiteral(): Boolean {
  var invoked: GrExpression = this
  while (true) {
    when (invoked) {
      is GrBuiltinTypeClassExpression -> return true
      is GrReferenceExpression -> return invoked.resolve() is PsiClass
      is GrIndexProperty -> {
        if (invoked.argumentList.allArguments.isNotEmpty()) return false
        invoked = invoked.invokedExpression
      }
      else -> return false
    }
  }
}

fun GrIndexProperty.getArrayClassType(): PsiType? {
  val arrayTypeBase = getClassReferenceFromExpression(this) ?: return null
  return TypesUtil.createJavaLangClassType(arrayTypeBase, project, resolveScope)
}

/**
 * If there is one argument (`foo[a]`), then result is a type of `a`.
 * If there is multiple arguments (`foo[a,b,c]`), then result is a list from Groovy perspective (as in `[a, b, c]` as a literal).
 *
 * @return type of the whole argument list or `null` if there are named arguments.
 */
fun GrIndexProperty.getArgumentListType(): PsiType? {
  val argList = argumentList
  if (argList.namedArguments.isNotEmpty()) return null
  argList.expressionArguments.singleOrNull()?.let { return it.type }
  val types = argList.expressionArguments.map { it.type }.toTypedArray()
  return GrImmediateTupleType(types, JavaPsiFacade.getInstance(project), resolveScope)
}

fun GrIndexProperty.getArgumentTypes(rhs: Boolean): Array<PsiType>? {
  val argumentListType = getArgumentListType() ?: return null
  if (rhs) {
    return arrayOf(argumentListType)
  }
  else {
    val rType = (parent as? GrAssignmentExpression)?.type ?: return null
    return arrayOf(argumentListType, rType)
  }
}

@JvmOverloads
fun GrIndexProperty.multiResolve(rhs: Boolean = true): Array<GroovyResolveResult> {
  return (if (rhs) rValueReference else lValueReference)?.multiResolve(false) ?: GroovyResolveResult.EMPTY_ARRAY
}

@JvmOverloads
fun GrIndexProperty.advancedResolve(rhs: Boolean = true): GroovyResolveResult {
  return PsiImplUtil.extractUniqueResult(multiResolve(rhs))
}
