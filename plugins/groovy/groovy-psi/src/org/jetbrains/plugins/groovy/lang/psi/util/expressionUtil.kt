// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyExpressionUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.groovy.lang.GroovyExpressionFilter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

private val EP_NAME = ExtensionPointName.create<GroovyExpressionFilter>("org.intellij.groovy.expressionFilter")

fun GrExpression.isFake(): Boolean {
  return EP_NAME.extensions.any {
    it.isFake(this)
  }
}
