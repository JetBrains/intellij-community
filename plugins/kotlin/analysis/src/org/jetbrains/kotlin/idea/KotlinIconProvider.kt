// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.util.hasMatchingExpected
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

class KotlinIconProvider : KotlinIconProviderBase() {
    override fun KtDeclaration.isMatchingExpected() = hasActualModifier() && hasMatchingExpected()

    compilation error
}