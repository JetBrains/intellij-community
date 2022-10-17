// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared

import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest

abstract class AbstractK2SharedQuickFixTest : AbstractQuickFixTest() {
    override fun checkForUnexpectedErrors() {}

    override fun isFirPlugin(): Boolean = true
}