// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.conversion.copy

import org.jetbrains.kotlin.idea.caches.resolve.forceCheckForResolveInDispatchThreadInTests

abstract class AbstractK1LiteralTextToKotlinCopyPasteTest : AbstractLiteralTextToKotlinCopyPasteTest() {
    override fun doPerformEditorAction(actionId: String) {
        forceCheckForResolveInDispatchThreadInTests {
            super.doPerformEditorAction(actionId)
        }
    }
}