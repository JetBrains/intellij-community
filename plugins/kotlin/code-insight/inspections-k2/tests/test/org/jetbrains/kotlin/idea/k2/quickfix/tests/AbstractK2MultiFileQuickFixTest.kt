// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest.Companion.ACTIONS_DIFFERENT_FROM_K1
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest.Companion.ACTIONS_NOT_IMPLEMENTED
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.actionsListDirectives
import org.jetbrains.kotlin.psi.KtFile
import java.io.File


abstract class AbstractK2MultiFileQuickFixTest: AbstractQuickFixMultiFileTest() {

    override fun checkForUnexpectedErrors(file: KtFile) {}

    override val actionPrefix: String? = "K2_ACTION:"

    override fun checkAvailableActionsAreExpected(
        file: File,
        actions: Collection<IntentionAction>
    ) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(
            this.file,
            dataFile(), actions,
            actionsToExclude = ACTIONS_NOT_IMPLEMENTED + ACTIONS_DIFFERENT_FROM_K1,
            actionsListDirectives = pluginMode.actionsListDirectives
        )
    }
}