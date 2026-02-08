// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
interface QuickFixContributor {
    companion object {
        internal val EP_NAME: ExtensionPointName<QuickFixContributor> = ExtensionPointName("org.jetbrains.kotlin.quickFixContributor")
    }

    fun registerQuickFixes(quickFixes: QuickFixes)
}