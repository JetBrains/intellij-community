// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

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
