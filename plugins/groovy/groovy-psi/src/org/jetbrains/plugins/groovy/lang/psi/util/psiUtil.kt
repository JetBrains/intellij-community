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
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause
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
    if (it is GrForClause && it.declaredVariable != owner) return true
    if (it is GrForInClause && it.delimiter.node.elementType == kIN) return true
    return owner.typeElementGroovy != null
  }
  is GrMethod -> owner.isConstructor || owner.returnTypeElementGroovy != null && !owner.hasTypeParameters()
  is GrVariable -> owner.typeElementGroovy != null
  is GrVariableDeclaration -> owner.typeElementGroovy != null
  else -> true
}

fun GrExpression?.isSuperExpression(): Boolean {
  val referenceExpression = this as? GrReferenceExpression
  return referenceExpression?.referenceNameElement?.node?.elementType == GroovyTokenTypes.kSUPER
}
