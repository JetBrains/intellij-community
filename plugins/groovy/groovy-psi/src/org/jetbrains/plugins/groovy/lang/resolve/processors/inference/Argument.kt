// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

data class Argument(val type:PsiType?, val expression: GrExpression? = null)