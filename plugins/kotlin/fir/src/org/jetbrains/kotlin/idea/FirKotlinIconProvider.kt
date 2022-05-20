// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.psi.KtDeclaration

internal class FirKotlinIconProvider : KotlinIconProvider() {
    override fun isMatchingExpected(declaration: KtDeclaration) = false
}