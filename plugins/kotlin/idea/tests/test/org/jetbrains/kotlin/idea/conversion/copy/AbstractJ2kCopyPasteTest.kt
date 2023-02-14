// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.conversion.copy

import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeatures

abstract class AbstractJ2kCopyPasteTest : AbstractCopyPasteTest() {
    protected open fun isNewJ2K(): Boolean = false

    override fun setUp() {
        super.setUp()
        ExperimentalFeatures.NewJ2k.isEnabled = isNewJ2K()
    }
}