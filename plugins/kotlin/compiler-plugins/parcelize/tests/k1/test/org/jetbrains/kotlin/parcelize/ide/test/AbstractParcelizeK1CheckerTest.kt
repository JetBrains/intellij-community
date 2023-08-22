// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.parcelize.ide.test

import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest

abstract class AbstractParcelizeK1CheckerTest : AbstractKotlinHighlightVisitorTest() {
    override fun setUp() {
        super.setUp()
        addParcelizeLibraries(module)
    }

    override fun tearDown() {
        try {
            removeParcelizeLibraries(module)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}