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
package org.jetbrains.plugins.groovy.intentions.other

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

object GrCopyStringConcatenationPredicate : PsiElementPredicate {

  override fun satisfiedBy(element: PsiElement): Boolean {
    if (element is GrLiteral && element.value is String) return true
    if (element !is GrBinaryExpression) return false
    if (element.operationTokenType !== GroovyTokenTypes.mPLUS) return false
    val left = element.leftOperand
    val right = element.rightOperand ?: return false
    return satisfiedBy(left) && satisfiedBy(right)
  }
}
