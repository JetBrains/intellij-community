// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractLiteralKotlinToKotlinCopyPasteTest : AbstractCopyPasteTest() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun doTest(unused: String) {
        val fileName = fileName()

        val testFile = dataFile()
        val expectedFile = File(testFile.parent, testFile.nameWithoutExtension + ".expected.kt")

        myFixture.configureByFile(fileName)

        myFixture.performEditorAction(IdeActions.ACTION_COPY)

        configureTargetFile(fileName.replace(".kt", ".to.kt"))

        myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        KotlinTestUtils.assertEqualsToFile(expectedFile, myFixture.file.text)
    }
}
