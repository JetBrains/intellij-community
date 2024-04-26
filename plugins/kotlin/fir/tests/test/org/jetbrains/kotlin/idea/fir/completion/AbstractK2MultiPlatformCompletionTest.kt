// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import org.jetbrains.kotlin.idea.completion.test.AbstractMultiPlatformCompletionTest

abstract class AbstractK2MultiPlatformCompletionTest : AbstractMultiPlatformCompletionTest() {
    override fun isFirPlugin(): Boolean = true
}