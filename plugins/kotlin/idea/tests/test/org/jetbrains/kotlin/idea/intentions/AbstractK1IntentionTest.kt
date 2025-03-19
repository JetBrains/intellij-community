// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File

abstract class AbstractK1IntentionTest : AbstractIntentionTestBase() {
    override fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1) {
            super.doTestFor(mainFile, pathToFiles, intentionAction, fileText)
        }
    }
}