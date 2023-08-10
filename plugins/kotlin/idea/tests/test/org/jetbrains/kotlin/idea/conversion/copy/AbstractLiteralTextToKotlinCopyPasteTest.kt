// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.caches.resolve.forceCheckForResolveInDispatchThreadInTests
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractLiteralTextToKotlinCopyPasteTest : AbstractCopyPasteTest() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun doTest(unused: String) {
        val fileName = fileName()
        val targetFileName = fileName.replace(".txt", ".kt")

        myFixture.configureByFile(fileName)
        val fileText = myFixture.editor.document.text

        if (!myFixture.editor.selectionModel.hasSelection())
            myFixture.editor.selectionModel.setSelection(0, fileText.length)

        forceCheckForResolveInDispatchThreadInTests {
            myFixture.performEditorAction(IdeActions.ACTION_COPY)
        }

        configureTargetFile(targetFileName)

        forceCheckForResolveInDispatchThreadInTests {
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        }

        val testFile = dataFile()
        val expectedFile = File(testFile.parent, testFile.nameWithoutExtension + ".expected.kt")

        KotlinTestUtils.assertEqualsToFile(expectedFile, myFixture.file.text)
    }
}
