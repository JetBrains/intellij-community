// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.extensions.impl.NamedArgumentDescriptorImpl
import org.jetbrains.plugins.groovy.extensions.impl.TypeCondition
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

class GroovyNamedVariantArgumentProvider : GroovyNamedArgumentProvider() {
  override fun getNamedArguments(call: GrCall,
                                 resolveResult: GroovyResolveResult,
                                 argumentName: String?,
                                 forCompletion: Boolean,
                                 result: MutableMap<String, NamedArgumentDescriptor>) {
    val method = resolveResult.element as? PsiMethod ?: return

    val parameters = method.parameterList.parameters
    val mapParameter = (if (parameters.isNotEmpty()) parameters[0] else null) ?: return

    collectNamedParams(mapParameter).forEach {
      val type = it.type
      result[it.name] = if (type != null) TypeCondition(type, it.navigationElement) else NamedArgumentDescriptorImpl(it.navigationElement)
    }
  }
}