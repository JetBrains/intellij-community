// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation

import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtDeclaration

class KotlinDeclarationNavigationPolicyImpl : KotlinDeclarationNavigationPolicy {
    override fun getOriginalElement(declaration: KtDeclaration) = SourceNavigationHelper.getOriginalElement(declaration)

    override fun getNavigationElement(declaration: KtDeclaration) = SourceNavigationHelper.getNavigationElement(declaration)
}
