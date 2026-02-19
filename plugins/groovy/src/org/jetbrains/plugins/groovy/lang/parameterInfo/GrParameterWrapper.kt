// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parameterInfo

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

internal data class GrParameterWrapper(val name: String?, val type: PsiType?, val defaultInitializer: GrExpression? = null) {
}