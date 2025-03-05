// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.actionsListDirectives
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK2QuickFixTest : AbstractQuickFixTest() {
    companion object {
        // TODO: We added the following list because there are some missing K2 actions. Remove this list when they are ready.
        val ACTIONS_NOT_IMPLEMENTED: List<String> = listOf("Rename reference")
        val ACTIONS_DIFFERENT_FROM_K1: List<String> = listOf("Make 'open'") // This look like a bug. See KTIJ-27687.
    }

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          { runInEdtAndWait { project.invalidateCaches() } },
          { super.tearDown() },
        )
    }

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForErrorsAfter(mainFile, ktFile, fileText)
    }

    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(
            file,
            dataFile(), actions,
            actionsToExclude = ACTIONS_NOT_IMPLEMENTED + ACTIONS_DIFFERENT_FROM_K1,
            actionsListDirectives = pluginMode.actionsListDirectives
        )
    }

    override fun getAfterFileName(beforeFileName: String): String {
        val afterFile = File(dataFilePath(beforeFileName.replace(".kt", ".k2.kt.after")))
        return if (afterFile.exists()) {
            afterFile.name
        } else {
            super.getAfterFileName(beforeFileName)
        }
    }

    override val actionPrefix: String? = "K2_ACTION:"
}