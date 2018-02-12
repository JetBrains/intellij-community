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


package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult.applicable
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult.inapplicable


open class ConstructorMapApplicabilityProvider : GroovyApplicabilityProvider() {

  open fun isConstructor(method: PsiMethod): Boolean {
    return method.isConstructor
  }

  override fun isApplicable(argumentTypes: Array<out PsiType>,
                            method: PsiMethod,
                            substitutor: PsiSubstitutor?,
                            place: PsiElement?,
                            eraseParameterTypes: Boolean): ApplicabilityResult? {
    if (!isConstructor(method)) return null

    val parameters = method.parameterList.parameters
    if (parameters.isEmpty() && argumentTypes.size == 1) {
      return if (isInheritor(argumentTypes[0], CommonClassNames.JAVA_UTIL_MAP)) applicable else inapplicable
    }
    return null
  }
}