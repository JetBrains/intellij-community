// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinPairMatcher
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import java.io.File

abstract class AbstractPairMatcherTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    protected fun doTest(path: String) {
        var docText = FileUtil.loadFile(File(path))
        val startPos = docText.indexOf("<start>")
        if (startPos == -1) {
            throw IllegalArgumentException("<start> marker not found in testdata")
        }
        docText = docText.replace("<start>", "")
        val bracePos = docText.indexOf("<brace>")
        if (bracePos == -1) {
            throw IllegalArgumentException("<brace> marker not found in testdata")
        }
        docText = docText.replace("<brace>", "")
        myFixture.configureByText(KotlinFileType.INSTANCE, docText)
        val pos = KotlinPairMatcher().getCodeConstructStart(myFixture.file, bracePos)
        assertEquals(startPos, pos)
    }
}
