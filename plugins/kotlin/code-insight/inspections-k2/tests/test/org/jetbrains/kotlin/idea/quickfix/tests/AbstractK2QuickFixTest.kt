// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.tests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.invalidateCaches
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

    override fun loadScriptConfiguration(file: KtFile) {
        if (!file.isScript()) return

        runWithModalProgressBlocking(project, "AbstractK2QuickFixTest") {
            KotlinScriptService.getInstance(project).load(file.alwaysVirtualFile)
        }
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() },
        )
    }

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForErrorsBefore(mainFile, ktFile, fileText)
    }

    override fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForErrorsAfter(mainFile, ktFile, fileText)
    }

    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {
        DirectiveBasedActionUtils.checkAvailableActionsAreExpected(
            file,
            dataFile(), actions,
            actionsToExclude = ACTIONS_NOT_IMPLEMENTED + ACTIONS_DIFFERENT_FROM_K1,
            actionsListDirectives = arrayOf(
                DirectiveBasedActionUtils.K2_ACTIONS_LIST_DIRECTIVE,
                DirectiveBasedActionUtils.K1_ACTIONS_LIST_DIRECTIVE
            )
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