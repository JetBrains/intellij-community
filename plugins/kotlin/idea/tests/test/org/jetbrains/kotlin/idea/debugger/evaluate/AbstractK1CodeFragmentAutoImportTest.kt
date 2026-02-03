// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

abstract class AbstractK1CodeFragmentAutoImportTest : AbstractCodeFragmentAutoImportTest() {
    override fun configureByCodeFragment(filePath: String) {
        myFixture.configureByK1ModeCodeFragment(filePath)
    }
}
