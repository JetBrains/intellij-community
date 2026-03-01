// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions.declarations

import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.AFTER_ERROR_DIRECTIVE
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1JoinLinesTest: AbstractJoinLinesTest() {
    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(
            ktFile,
            directive = AFTER_ERROR_DIRECTIVE,
            diagnosticsProvider = k1DiagnosticsProvider
        )
    }
}

