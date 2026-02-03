// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.immutable

import com.intellij.psi.JavaPsiFacade
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.lang.GroovyConstructorNamedArgumentProvider.processClass
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder

class CopyWithNamedArgumentProvider : GroovyNamedArgumentProvider() {

  override fun getNamedArguments(call: GrCall,
                                 resolveResult: GroovyResolveResult,
                                 argumentName: String?,
                                 forCompletion: Boolean,
                                 result: MutableMap<String, NamedArgumentDescriptor>) {
    val method = resolveResult.element as? GrLightMethodBuilder ?: return
    if (method.methodKind != immutableCopyWithKind) return
    val containingClass = method.containingClass ?: return
    val type = JavaPsiFacade.getElementFactory(call.project).createType(containingClass, resolveResult.substitutor)
    processClass(call, type, argumentName, result)
  }
}
