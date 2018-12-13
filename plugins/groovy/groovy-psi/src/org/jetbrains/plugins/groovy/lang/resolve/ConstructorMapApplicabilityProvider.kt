// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.applicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.inapplicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

open class ConstructorMapApplicabilityProvider : GroovyApplicabilityProvider() {

  open fun isConstructor(method: PsiMethod): Boolean = method.isConstructor

  override fun isApplicable(arguments: Arguments, method: PsiMethod): Applicability? {
    if (!isConstructor(method)) return null

    val parameters = method.parameterList.parameters
    if (parameters.isNotEmpty()) return null
    val argument = arguments.singleOrNull() ?: return null
    val argumentType = argument.type
    return if (isInheritor(argumentType, CommonClassNames.JAVA_UTIL_MAP)) applicable else inapplicable
  }
}
