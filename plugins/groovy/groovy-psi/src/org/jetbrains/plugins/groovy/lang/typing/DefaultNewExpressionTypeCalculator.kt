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
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnonymousClassType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType

class DefaultNewExpressionTypeCalculator : GrTypeCalculator<GrNewExpression> {

  override fun getType(expression: GrNewExpression): PsiType? {
    return getAnonymousType(expression) ?:
           getRegularType(expression)
  }

  private fun getAnonymousType(expression: GrNewExpression): PsiType? {
    val anonymous = expression.anonymousClassDefinition ?: return null
    return GrAnonymousClassType(
      LanguageLevel.JDK_1_5,
      anonymous.resolveScope,
      JavaPsiFacade.getInstance(expression.project),
      anonymous
    )
  }

  private fun getRegularType(expression: GrNewExpression): PsiType? {
    var type: PsiType = expression.referenceElement?.let { GrClassReferenceType(it) } ?:
                        (expression.typeElement as? GrBuiltInTypeElement)?.type ?:
                        return null
    repeat(expression.arrayCount) {
      type = type.createArrayType()
    }
    return type
  }
}