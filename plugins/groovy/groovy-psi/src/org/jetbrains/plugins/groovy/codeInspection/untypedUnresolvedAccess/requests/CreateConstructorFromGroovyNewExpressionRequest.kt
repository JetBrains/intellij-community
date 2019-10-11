// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression

internal class CreateConstructorFromGroovyNewExpressionRequest(
  newExpression: GrNewExpression,
  modifiers: Collection<JvmModifier>
) : CreateExecutableFromGroovyUsageRequest<GrNewExpression>(newExpression, modifiers), CreateConstructorRequest
