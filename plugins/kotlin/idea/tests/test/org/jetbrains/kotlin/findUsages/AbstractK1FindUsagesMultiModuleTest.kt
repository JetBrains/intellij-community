// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.findUsages

import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest.Companion.FindUsageTestType

abstract class AbstractK1FindUsagesMultiModuleTest: AbstractFindUsagesMultiModuleTest() {
    override fun getTestType(): FindUsageTestType {
        return FindUsageTestType.DEFAULT
    }
}

