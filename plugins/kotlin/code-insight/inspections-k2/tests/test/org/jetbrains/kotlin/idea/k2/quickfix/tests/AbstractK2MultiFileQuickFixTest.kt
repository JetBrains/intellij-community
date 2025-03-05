// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.psi.KtFile


abstract class AbstractK2MultiFileQuickFixTest: AbstractQuickFixMultiFileTest() {

    override fun checkForUnexpectedErrors(file: KtFile) {}

    override val actionPrefix: String? = "K2_ACTION:"
}