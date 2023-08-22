// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractCopyPasteTest : KotlinLightCodeInsightFixtureTestCase() {
    private var savedImportsOnPasteSetting: Int = 0
    private val DEFAULT_TO_FILE_TEXT = "package to\n\n<caret>"

    override fun setUp() {
        super.setUp()
        savedImportsOnPasteSetting = CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE
        CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = savedImportsOnPasteSetting },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected fun configureByDependencyIfExists(dependencyFileName: String): PsiFile? {
        val file = dataFile(dependencyFileName)
        if (!file.exists()) return null
        return if (dependencyFileName.endsWith(".java")) {
            //allow test framework to put it under right directory
            myFixture.addClass(FileUtil.loadFile(file, true)).containingFile
        } else {
            myFixture.configureByFile(dependencyFileName)
        }
    }

    protected fun configureTargetFile(fileName: String): KtFile {
        return if (dataFile(fileName).exists()) {
            myFixture.configureByFile(fileName) as KtFile
        } else {
            myFixture.configureByText(fileName, DEFAULT_TO_FILE_TEXT) as KtFile
        }
    }
}

