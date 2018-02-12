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
package org.jetbrains.plugins.groovy.lang.sam

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.*
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE

class ClosureToSamConverter : GrTypeConverter() {

  private val myPositions = setOf(ASSIGNMENT, RETURN_VALUE, METHOD_PARAMETER)

  override fun isApplicableTo(position: ApplicableTo) = position in myPositions

  override fun isConvertibleEx(targetType: PsiType, actualType: PsiType, context: GroovyPsiElement,
                               currentPosition: ApplicableTo): ConversionResult? {
    if (targetType !is PsiClassType || actualType !is GrClosureType) return null
    if (!isSamConversionAllowed(context)) return null

    val result = targetType.resolveGenerics()
    val targetClass = result.element ?: return null

    val targetFqn = targetClass.qualifiedName ?: return null // anonymous classes has no fqn
    if (targetFqn == GROOVY_LANG_CLOSURE) return null

    findSingleAbstractSignature(targetClass) ?: return null
    return ConversionResult.OK
  }
}
