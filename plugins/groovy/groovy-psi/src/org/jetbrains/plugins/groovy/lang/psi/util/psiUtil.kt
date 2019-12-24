// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.lang.jvm.types.JvmArrayType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.groovy.lang.GroovyElementFilter
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_NULL
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl

/**
 * @param owner modifier list owner
 *
 * @return
 * * `true` when owner has explicit type or it's not required for owner to have explicit type
 * * `false` when doesn't have explicit type and it's required to have a type or modifier
 * * `defaultValue` for the other owners
 *
 */
fun modifierListMayBeEmpty(owner: PsiElement?): Boolean = when (owner) {
  is GrParameter -> owner.parent.let {
    if (it is GrParameterList) return true
    if (it is GrForInClause && it.delimiter.node.elementType == kIN) return true
    return owner.typeElementGroovy != null
  }
  is GrMethod -> owner.isConstructor || owner.returnTypeElementGroovy != null && !owner.hasTypeParameters()
  is GrVariable -> owner.typeElementGroovy != null
  is GrVariableDeclaration -> owner.typeElementGroovy != null
  else -> true
}

fun GrExpression?.isSuperExpression(): Boolean {
  return this is GrReferenceExpression && referenceNameElement?.node?.elementType === GroovyTokenTypes.kSUPER
}

fun GrExpression?.isThisExpression(): Boolean {
  return this is GrReferenceExpression && referenceNameElement?.node?.elementType === GroovyTokenTypes.kTHIS
}

fun GrOperatorExpression.multiResolve(): Array<out GroovyResolveResult> {
  return reference?.multiResolve(false) ?: GroovyResolveResult.EMPTY_ARRAY
}

val PsiMethod.isEffectivelyVarArgs: Boolean get() = isVarArgs || parameters.lastOrNull()?.type is JvmArrayType

val PsiParameter.isOptional get() = this is GrParameter && this.isOptional

fun elementInfo(element: PsiElement): String = "Element: $element; class: ${element.javaClass}; text: ${element.text}"

fun GrCodeReferenceElement.mayContainTypeArguments(): Boolean {
  val (parent, _) = skipParentsOfType<GrCodeReferenceElement>() ?: return true
  return parent !is GrImportStatement
}

fun GrExpression?.isNullLiteral(): Boolean {
  return this is GrLiteral && GrLiteralImpl.getLiteralType(this) == KW_NULL
}

fun GrExpression?.skipParenthesesDown(): GrExpression? {
  var current = this
  while (current is GrParenthesizedExpression) {
    current = current.operand
  }
  return current
}

private val EP_NAME = ExtensionPointName.create<GroovyElementFilter>("org.intellij.groovy.elementFilter")

fun GroovyPsiElement.isFake(): Boolean {
  return EP_NAME.extensionList.any {
    it.isFake(this)
  }
}
