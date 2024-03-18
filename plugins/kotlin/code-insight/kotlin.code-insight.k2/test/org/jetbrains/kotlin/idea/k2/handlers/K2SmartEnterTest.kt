// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.handlers

import org.jetbrains.kotlin.idea.completion.test.AbstractSmartEnterTest

class K2SmartEnterTest: AbstractSmartEnterTest() {
    override fun isFirPlugin(): Boolean = true
}