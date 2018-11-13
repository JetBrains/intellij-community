// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult.applicable
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult.inapplicable

open class ConstructorMapApplicabilityProvider : GroovyApplicabilityProvider() {

  open fun isConstructor(method: PsiMethod): Boolean = method.isConstructor

  override fun isApplicable(argumentTypes: Array<PsiType>, method: PsiMethod): ApplicabilityResult? {
    if (!isConstructor(method)) return null

    val parameters = method.parameterList.parameters
    if (parameters.isNotEmpty()) return null
    val argumentType = argumentTypes.singleOrNull() ?: return null
    return if (isInheritor(argumentType, CommonClassNames.JAVA_UTIL_MAP)) applicable else inapplicable
  }
}
