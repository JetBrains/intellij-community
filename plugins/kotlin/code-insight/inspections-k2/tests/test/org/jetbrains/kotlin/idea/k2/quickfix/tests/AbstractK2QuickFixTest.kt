// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractK2QuickFixTest : AbstractQuickFixTest() {
    companion object {
        // TODO: We added the following list because there are some missing K2 actions. Remove this list when they are ready.
        val ACTIONS_NOT_IMPLEMENTED: List<String> = listOf("Rename reference")
        val ACTIONS_DIFFERENT_FROM_K1: List<String> = listOf("Make 'open'") // This look like a bug. See KTIJ-27687.
    }

    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          { runInEdtAndWait { project.invalidateCaches() } },
          { super.tearDown() }
        )
    }

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override fun checkForUnexpectedErrors() {}

    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(
            dataFile(), actions, actionsToExclude = ACTIONS_NOT_IMPLEMENTED + ACTIONS_DIFFERENT_FROM_K1,
        )
    }

    override fun getAfterFileName(beforeFileName: String): String {
        val firAfterFile = File(dataFilePath(beforeFileName.replace(".kt", ".fir.kt.after")))
        return if (firAfterFile.exists()) {
            firAfterFile.name
        } else {
            super.getAfterFileName(beforeFileName)
        }
    }
}