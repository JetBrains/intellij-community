// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinAutoImportCallableWeigher
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
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

        // enable extensions if there is a corresponding directive in the test.
        if (InTextDirectivesUtils.isDirectiveDefined(fileText, ENABLE_CALL_EXTENSIONS_DIRECTIVE)) {
            KotlinAutoImportCallableWeigher.EP_NAME.point.registerExtension(
                MockAutoImportCallableWeigher(),
                testRootDisposable
            )
        }

        fixture.configureByFile(fileName())

        var actualVariants: List<AutoImportVariant>? = null
        val executeListener = object : KotlinAddImportActionInfo.ExecuteListener {
            override fun onExecute(variants: List<AutoImportVariant>) {
                actualVariants = actualVariants.orEmpty() + variants
            }
        }
        KotlinAddImportActionInfo.setExecuteListener(file, testRootDisposable, executeListener)

        (StatisticsManager.getInstance() as StatisticsManagerImpl).enableStatistics(myFixture.testRootDisposable)
        increaseUseCountOf(InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, INCREASE_USE_COUNT_DIRECTIVE))

        myFixture.availableIntentions
            .filter { it.familyName == "Import" }
            .ifEmpty { error("No import fix available") }
            .forEach { importFix -> importFix.invoke(project, editor, file) }

        assertNotNull(actualVariants)

        val expectedVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, EXPECT_VARIANT_IN_ORDER_DIRECTIVE)
        val expectedAbsentVariantNames = InTextDirectivesUtils.findListWithPrefixes(fixture.file.text, EXPECT_VARIANT_NOT_PRESENT_DIRECTIVE)

        if (expectedVariantNames.isNotEmpty() || expectedAbsentVariantNames.isNotEmpty()) {
            val actualVariantNames = actualVariants.orEmpty().map { it.debugRepresentation }

            for (i in expectedVariantNames.indices) {
                assertTrue(
                    "mismatch at #$i: '${actualVariantNames[i]}' should start with '${expectedVariantNames[i]}'\nactual:\n${
                        actualVariantNames.joinToString("\n") { "// ${EXPECT_VARIANT_IN_ORDER_DIRECTIVE} \"$it\"" }
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

    private fun increaseUseCountOf(statisticsInfoValues: List<String>) {
        statisticsInfoValues.forEach { value ->
            StatisticsManager.getInstance().incUseCount(StatisticsInfo("", value))
        }
    }

    companion object {
        private const val EXPECT_VARIANT_IN_ORDER_DIRECTIVE: String = "EXPECT_VARIANT_IN_ORDER"
        private const val EXPECT_VARIANT_NOT_PRESENT_DIRECTIVE: String = "EXPECT_VARIANT_NOT_PRESENT"
        private const val INCREASE_USE_COUNT_DIRECTIVE: String = "INCREASE_USE_COUNT"
        private const val ENABLE_CALL_EXTENSIONS_DIRECTIVE: String = "ENABLE_CALL_EXTENSIONS"
    }
}

/**
 * This class mocks the [KotlinAutoImportCallableWeigher] in tests.
 * To illustrate the extension work, it gives more priority to functions with the specific annotation,
 * when they are called from the functions, annotated the same way.
 */
private class MockAutoImportCallableWeigher : KotlinAutoImportCallableWeigher {
    override fun KaSession.weigh(
        symbolToBeImported: KaCallableSymbol,
        unresolvedReferenceExpression: KtNameReferenceExpression
    ): Int {
        val symbolAnnotated = TEST_ANNOTATION_CLASS_ID in symbolToBeImported.annotations
        val receiverAnnotated =
            unresolvedReferenceExpression.parentOfType<KtFunction>()?.symbol?.annotations?.contains(TEST_ANNOTATION_CLASS_ID)
        return if (symbolAnnotated && receiverAnnotated == true) 1 else 0
    }

    override fun KaSession.weigh(
        symbolToBeImported: KaClassSymbol,
        unresolvedReferenceExpression: KtNameReferenceExpression
    ): Int {
        val symbolAnnotated = TEST_ANNOTATION_CLASS_ID in symbolToBeImported.annotations
        val receiverAnnotated =
            unresolvedReferenceExpression.parentOfType<KtFunction>()?.symbol?.annotations?.contains(TEST_ANNOTATION_CLASS_ID)
        return if (symbolAnnotated && receiverAnnotated == true) 1 else 0
    }

    companion object {
        private val TEST_ANNOTATION_CLASS_ID: ClassId = ClassId.fromString("my/test/MyAnnotation")
    }
}
