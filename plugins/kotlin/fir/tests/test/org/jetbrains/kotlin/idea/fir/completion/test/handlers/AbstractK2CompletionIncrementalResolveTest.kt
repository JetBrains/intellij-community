// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import org.jetbrains.kotlin.idea.completion.test.AbstractCompletionIncrementalResolveTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractK2CompletionIncrementalResolveTest : AbstractCompletionIncrementalResolveTest() {
    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}
