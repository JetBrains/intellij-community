// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.copyright

import org.jetbrains.kotlin.copyright.AbstractUpdateKotlinCopyrightTest

abstract class AbstractFirUpdateKotlinCopyrightTest: AbstractUpdateKotlinCopyrightTest() {

    override fun isFirPlugin(): Boolean {
        return true
    }
}