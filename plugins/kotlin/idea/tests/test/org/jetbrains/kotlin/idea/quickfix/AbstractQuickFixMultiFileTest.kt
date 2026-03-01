// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.actionsListDirectives
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1QuickFixMultiFileTest : AbstractQuickFixMultiFileTest() {

    override fun checkForUnexpectedErrors(file: KtFile) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(file, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)
    }

    override fun checkAvailableActionsAreExpected(file: File, actions: Collection<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(
            psiFile = this.file,
            file = file,
            availableActions = availableActions,
            actionsListDirectives = pluginMode.actionsListDirectives
        )
    }
}