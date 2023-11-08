// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractHighLevelQuickFixTest : AbstractQuickFixTest() {
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun isFirPlugin(): Boolean = true
    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )

    }

    override val disableTestDirective: String get() =  IgnoreTests.DIRECTIVES.IGNORE_K2_MULTILINE_COMMENT

    override fun doTest(beforeFileName: String) {
        val firBeforeFileName = getFirBeforeFileName(beforeFileName)
        super.doTest(firBeforeFileName)
    }

    private fun getFirBeforeFileName(beforeFileName: String): String {
        val firBeforeFilename = beforeFileName.replace(".kt", ".fir.kt")
        val firBeforeFile = File(firBeforeFilename)
        return if (firBeforeFile.exists()) {
            firBeforeFile.canonicalPath
        } else {
            beforeFileName
        }
    }

    override fun getAfterFileName(beforeFileName: String): String {
        val firAfterFile = File(dataFilePath(beforeFileName.replace(".kt", ".fir.kt.after")))
        return if (firAfterFile.exists()) {
            firAfterFile.name
        } else {
            super.getAfterFileName(beforeFileName)
        }
    }

    override val shouldCheckIntentionActionType: Boolean
        // All quickfixes from the FIR plugin are guaranteed to be of type QuickFixActionBase (see HLDiagnosticFixFactory)
        get() = true

    // TODO: Enable these as more actions/inspections are enabled, and/or add more FIR-specific directives
    override fun checkForUnexpectedErrors() {}
    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {}
    override fun parseInspectionsToEnable(beforeFileName: String, beforeFileText: String): List<InspectionProfileEntry> {
        return emptyList()
    }
}
