// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtBinaryExpression

/*
Utils below are required to overcome poorly selected PSI elements
on UnresolvedReference diagnostics for complex assignments (like `+=`).

See KT-75331 for more info.
*/

@get:ApiStatus.Internal
val PsiElement.operationReferenceForBinaryExpressionOrThis: PsiElement
    get() = (this as? KtBinaryExpression)?.operationReference ?: this
