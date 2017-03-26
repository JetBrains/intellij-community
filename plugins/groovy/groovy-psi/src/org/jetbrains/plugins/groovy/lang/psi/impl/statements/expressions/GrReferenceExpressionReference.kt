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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper

class GrReferenceExpressionReference(private val ref: GrReferenceExpressionImpl, private val forceRValue: Boolean)
  : PsiPolyVariantReferenceBase<GrReferenceExpressionImpl>(ref) {

  override fun getVariants(): Array<out Any> = ArrayUtil.EMPTY_OBJECT_ARRAY

  override fun multiResolve(incompleteCode: Boolean): Array<out GroovyResolveResult> {
    val results = TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode) { ref, incomplete ->
      ref.ref.doPolyResolve(incompleteCode, forceRValue)
    }
    return if (results.isEmpty()) GroovyResolveResult.EMPTY_ARRAY else results
  }
}