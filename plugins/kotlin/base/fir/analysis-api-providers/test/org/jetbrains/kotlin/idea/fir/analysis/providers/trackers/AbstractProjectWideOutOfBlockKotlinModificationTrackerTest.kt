// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers.trackers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import junit.framework.Assert
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractProjectWideOutOfBlockKotlinModificationTrackerTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun doTest(path: String) {
        val testDataFile = File(path)
        val fileText = FileUtil.loadFile(testDataFile)
        myFixture.configureByText(testDataFile.name, fileText)
        val textToType = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TYPE:") ?: DEFAULT_TEXT_TO_TYPE
        val outOfBlock = InTextDirectivesUtils.getPrefixedBoolean(fileText, "// OUT_OF_BLOCK:")
            ?: error("Please, specify should out of block change happen or not by `// OUT_OF_BLOCK:` directive")
        val tracker = project.createProjectWideOutOfBlockModificationTracker()
        val initialModificationCount = tracker.modificationCount
        myFixture.type(textToType)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val afterTypingModificationCount = tracker.modificationCount
        Assert.assertEquals(outOfBlock, initialModificationCount != afterTypingModificationCount)
    }

    companion object {
        const val DEFAULT_TEXT_TO_TYPE = "hello"
    }
}