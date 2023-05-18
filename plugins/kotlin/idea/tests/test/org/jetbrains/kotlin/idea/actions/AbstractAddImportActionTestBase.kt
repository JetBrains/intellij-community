// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractAddImportActionTestBase : KotlinLightCodeInsightFixtureTestCase() {

    protected open fun doTest(unused: String) {
        val fixture = myFixture

        val dependencySuffixes = listOf(".dependency.kt", ".dependency1.kt", ".dependency2.kt")
        for (suffix in dependencySuffixes) {
            val depFilePath = fileName().replace(".kt", suffix)
            if (File(testDataDirectory, depFilePath).exists()) {
                fixture.configureByFile(depFilePath)
            }
        }

        val mainFile = dataFile()
        val fileText = FileUtil.loadFile(mainFile, true)
        assertTrue("\"<caret>\" is missing in file \"$mainFile\"", fileText.contains("<caret>"))

        fixture.configureByFile(fileName())

        var actualVariants: List<List<String>>? = null
        val executeListener = object : KotlinAddImportActionInfo.ExecuteListener {
            override fun onExecute(variants: List<List<String>>) {
                assertNull(actualVariants)
                actualVariants = variants
            }
        }
        KotlinAddImportActionInfo.setExecuteListener(testRootDisposable, executeListener)

        val importFix = myFixture.availableIntentions.singleOrNull { it.familyName == "Import" } ?: error("No import fix available")
        importFix.invoke(project, editor, file)

        assertNotNull(actualVariants)

        val expectedVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, "EXPECT_VARIANT_IN_ORDER")
        val expectedAbsentVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, "EXPECT_VARIANT_NOT_PRESENT")

        if (expectedVariantNames.isNotEmpty() || expectedAbsentVariantNames.isNotEmpty()) {
            val actualVariantNames = actualVariants!!.flatten()

            for (i in expectedVariantNames.indices) {
                assertTrue(
                    "mismatch at #$i: '${actualVariantNames[i]}' should start with '${expectedVariantNames[i]}'\nactual:\n${
                        actualVariantNames.joinToString("\n") { "// EXPECT_VARIANT_IN_ORDER \"$it\"" }
                    }",
                    actualVariantNames[i].contains(expectedVariantNames[i])
                )
            }

            for (expectedAbsentVariant in expectedAbsentVariantNames) {
                assertTrue("$expectedAbsentVariant should not be present",
                           actualVariantNames.none { it.contains(expectedAbsentVariant) })
            }
        }

        myFixture.checkResultByFile("${fileName()}.after")
    }
}
