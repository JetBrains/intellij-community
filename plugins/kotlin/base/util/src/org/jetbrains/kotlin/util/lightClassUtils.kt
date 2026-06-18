// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiMethods
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol

context(_: KaSession)
fun KaCallableSymbol.getPsiMethods(): List<PsiMethod> =
    when (this) {
        is KaFunctionSymbol -> asPsiMethods()
        is KaPropertySymbol -> listOfNotNull(getter?.asPsiMethods(), setter?.asPsiMethods()).flatten()
        else -> emptyList()
    }
