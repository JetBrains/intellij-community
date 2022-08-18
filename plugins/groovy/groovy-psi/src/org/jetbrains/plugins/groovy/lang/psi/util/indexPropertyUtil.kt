// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyIndexPropertyUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.impl.GrImmediateTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.getClassReferenceFromExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.UnknownArgument
import org.jetbrains.plugins.groovy.lang.typing.ListLiteralType

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
  return TypesUtil.createJavaLangClassType(arrayTypeBase, this)
}

class ListArgument(
  private val expressions: List<GrExpression>,
  private val context: PsiElement
) : Argument {

  override val type: PsiType? by lazyPub {
    ListLiteralType(expressions, context)
  }

  fun unwrap(): Arguments {
    return expressions.map(::ExpressionArgument)
  }
}

private fun tupleType(expressions: Array<out GrExpression>, context: PsiElement): GrImmediateTupleType {
  val types = expressions.map { it.type }
  return GrImmediateTupleType(types, JavaPsiFacade.getInstance(context.project), context.resolveScope)
}

/**
 * If there is one argument (`foo[a]`), then result is `a` as is.
 * If there is multiple arguments (`foo[a,b,c]`), then result is a list from Groovy perspective (as in `[a, b, c]` as a literal).
 *
 * @return argument of the whole argument list or [UnknownArgument] if there are named arguments.
 */
fun GrIndexProperty.getArgumentListArgument(): Argument {
  val argList = argumentList
  if (argList.namedArguments.isNotEmpty()) {
    return UnknownArgument
  }
  val expressions = argList.expressionArguments
  val singleExpression = expressions.singleOrNull()
  if (singleExpression != null) {
    return ExpressionArgument(singleExpression)
  }
  else {
    return ListArgument(expressions.toList(), this)
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
