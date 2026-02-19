// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1QuickFixMultiModuleTest : AbstractQuickFixMultiModuleTest() {
    override fun checkForUnexpectedErrors(actionFile: KtFile) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(
            actionFile,
            DirectiveBasedActionUtils.ERROR_DIRECTIVE,
            k1DiagnosticsProvider
        )
    }
}

