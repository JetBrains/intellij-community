// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument

internal class CreateConstructorFromGroovyUsageRequest(
  call: GrConstructorCall,
  modifiers: Collection<JvmModifier>
) : CreateExecutableFromGroovyUsageRequest<GrConstructorCall>(call, modifiers), CreateConstructorRequest {

  override fun getArguments(): List<Argument>? {
    return call.constructorReference?.arguments
  }
}
