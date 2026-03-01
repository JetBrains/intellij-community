// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.idea.util.expectedDeclarationIfAny
import org.jetbrains.kotlin.psi.KtDeclaration

@K1Deprecation
class KotlinExpectActualSupportImpl: ExpectActualSupport {
    override fun actualsForExpect(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> =
        declaration.actualsForExpected(module)

    override fun expectDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? =
        declaration.expectedDeclarationIfAny()

}