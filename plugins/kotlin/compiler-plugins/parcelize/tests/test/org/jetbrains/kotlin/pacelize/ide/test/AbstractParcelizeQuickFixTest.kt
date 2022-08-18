// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.pacelize.ide.test

import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest

abstract class AbstractParcelizeQuickFixTest : AbstractQuickFixTest() {
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