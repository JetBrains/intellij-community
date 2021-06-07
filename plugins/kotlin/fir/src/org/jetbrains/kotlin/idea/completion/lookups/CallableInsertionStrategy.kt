// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import com.intellij.codeInsight.completion.InsertionContext

internal sealed class CallableInsertionStrategy {
    object AsCall : CallableInsertionStrategy()
    object AsIdentifier : CallableInsertionStrategy()
    class AsIdentifierCustom(val insertionHandlerAction: InsertionContext.() -> Unit) : CallableInsertionStrategy()
}