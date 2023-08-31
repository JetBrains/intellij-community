// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiFileTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractHighLevelQuickFixMultiFileTest : AbstractQuickFixMultiFileTest() {
    override fun isFirPlugin(): Boolean = true
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun doTestWithExtraFile(beforeFileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(beforeFileName),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2_MULTILINE_COMMENT,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE,
            computeAdditionalFiles = { mainTestFile -> listOfNotNull(mainTestFile.getAfterFileIfExists()) },
            test = { super.doTestWithExtraFile(beforeFileName) }
        )
    }

    override val captureExceptions: Boolean = false

    override fun checkForUnexpectedErrors(file: KtFile) {}

    override fun checkAvailableActionsAreExpected(file: File, actions: Collection<IntentionAction>) {}

    private fun Path.getAfterFileIfExists(): Path? {
        val afterFileName = fileName.toString().removeSuffix(".before.Main.kt") + ".after.kt"

        return resolveSibling(afterFileName).takeIf(Files::exists)
    }
}