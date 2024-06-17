// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.trackers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.Assert
import org.jetbrains.kotlin.analysis.api.platform.modification.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractProjectWideOutOfBlockKotlinModificationTrackerTest : KotlinLightCodeInsightFixtureTestCase() {

    fun doTest(path: String) {
        val testDataFile = File(path)
        val fileText = FileUtil.loadFile(testDataFile)
        myFixture.configureByText(testDataFile.name, fileText)

        val outOfBlock = InTextDirectivesUtils.getPrefixedBoolean(fileText, "// OUT_OF_BLOCK:")
            ?: error("Please, specify should out of block change happen or not by `// OUT_OF_BLOCK:` directive")

        val tracker = project.createProjectWideOutOfBlockModificationTracker()
        val initialModificationCount = tracker.modificationCount

        applyModificationCommand(fileText)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val afterTypingModificationCount = tracker.modificationCount
        Assert.assertEquals(outOfBlock, initialModificationCount != afterTypingModificationCount)
    }

    private fun applyModificationCommand(fileText: String) {
        val shouldDeleteLine = InTextDirectivesUtils.isDirectiveDefined(fileText, "// DELETE_LINE")
        val textToType = InTextDirectivesUtils.findLineWithPrefixRemoved(fileText, "// TYPE:")

        if (shouldDeleteLine && textToType != null) {
            error("Cannot both delete a line and type text in the same test (as the order would be undefined).")
        }

        if (shouldDeleteLine) {
            LightPlatformCodeInsightTestCase.deleteLine(myFixture.editor, project)
        } else {
            myFixture.type(textToType ?: DEFAULT_TEXT_TO_TYPE)
        }
    }

    companion object {
        const val DEFAULT_TEXT_TO_TYPE = "hello"
    }
}