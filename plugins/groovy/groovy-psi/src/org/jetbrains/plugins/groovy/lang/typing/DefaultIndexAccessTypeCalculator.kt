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

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.advancedResolve
import org.jetbrains.plugins.groovy.lang.psi.util.getArrayClassType
import org.jetbrains.plugins.groovy.lang.psi.util.getSimpleArrayAccessType
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.extractReturnTypeFromCandidate

class DefaultIndexAccessTypeCalculator : GrTypeCalculator<GrIndexProperty> {

  override fun getType(expression: GrIndexProperty): PsiType? {
    return expression.getArrayClassType() ?:
           expression.getSimpleArrayAccessType() ?:
           extractReturnTypeFromCandidate(expression.advancedResolve(), expression, null)
  }
}