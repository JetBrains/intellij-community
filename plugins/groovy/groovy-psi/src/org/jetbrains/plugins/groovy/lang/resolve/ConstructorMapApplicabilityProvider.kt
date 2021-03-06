// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
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
    return if (isInheritor(argumentType, CommonClassNames.JAVA_UTIL_MAP) && !hasOtherMapConstructor(method)) applicable else inapplicable
  }

  private fun hasOtherMapConstructor(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    return containingClass.constructors?.filter { constructor ->
      val argumentType = constructor.parameterList.parameters?.singleOrNull()?.type as? PsiClassType ?: return@filter false
      val isSubtypeOfMap = isInheritor(argumentType, CommonClassNames.JAVA_UTIL_MAP)
      if (isSubtypeOfMap) return true
      val map = GroovyPsiElementFactory.getInstance(method.project).createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP)
      val argumentQualifiedName = argumentType.resolve()?.qualifiedName ?: return@filter false
      return isInheritor(map, argumentQualifiedName)
    }?.isNotEmpty() ?: false
  }
}
