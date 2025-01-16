// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractHighLevelQuickFixTest : AbstractQuickFixTest() {
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() },
        )

    }

    override fun doTest(beforeFileName: String) {
        val effectiveBeforeFileName = getK2BeforeFileName(beforeFileName)
        super.doTest(effectiveBeforeFileName)
    }

    private fun getK2BeforeFileName(beforeFileName: String): String {
        val beforeFilename = beforeFileName.replace(".kt", ".k2.kt")
        val beforeFile = File(beforeFilename)
        return if (beforeFile.exists()) {
            beforeFile.canonicalPath
        } else {
            beforeFileName
        }
    }

    override fun getAfterFileName(beforeFileName: String): String {
        val afterFile = File(dataFilePath(beforeFileName.replace(".kt", ".k2.kt.after")))
        return if (afterFile.exists()) {
            afterFile.name
        } else {
            super.getAfterFileName(beforeFileName)
        }
    }

    // TODO: Enable these as more actions/inspections are enabled, and/or add more FIR-specific directives
    override fun checkForUnexpectedErrors() {}

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override val actionPrefix: String? = "K2_ACTION:"

    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {}
}