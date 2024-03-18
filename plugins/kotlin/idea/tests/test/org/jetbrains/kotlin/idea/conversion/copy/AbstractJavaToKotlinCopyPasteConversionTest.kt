// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.test.assertEquals

abstract class AbstractJavaToKotlinCopyPasteConversionTest : AbstractJ2kCopyPasteTest() {
    private var oldEditorOptions: KotlinEditorOptions? = null

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("copyPaste/conversion")

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()
        oldEditorOptions = KotlinEditorOptions.getInstance().state
        KotlinEditorOptions.getInstance().isEnableJavaToKotlinConversion = true
        KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { oldEditorOptions?.let { KotlinEditorOptions.getInstance().loadState(it) } },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun doTest(unused: String) {
        val path = dataFilePath(fileName())
        withCustomCompilerOptions(File(path).readText(), project, module) {
            val baseName = fileName().replace(".java", "")
            myFixture.configureByFiles("$baseName.java")

            val fileText = myFixture.editor.document.text
            val noConversionExpected = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NO_CONVERSION_EXPECTED").isNotEmpty()

            myFixture.performEditorAction(IdeActions.ACTION_COPY)

            configureByDependencyIfExists("$baseName.dependency.kt")
            configureByDependencyIfExists("$baseName.dependency.java")

            configureTargetFile("$baseName.to.kt")

            ConvertJavaCopyPasteProcessor.conversionPerformed = false

            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
            UIUtil.dispatchAllInvocationEvents()

            assertEquals(
                noConversionExpected, !ConvertJavaCopyPasteProcessor.conversionPerformed,
                if (noConversionExpected) "Conversion to Kotlin should not be suggested" else "No conversion to Kotlin suggested"
            )

            val actualText = (myFixture.file as KtFile).dumpTextWithErrors()
            KotlinTestUtils.assertEqualsToFile(File(path.replace(".java", ".expected.kt")), actualText)
        }
    }
}
