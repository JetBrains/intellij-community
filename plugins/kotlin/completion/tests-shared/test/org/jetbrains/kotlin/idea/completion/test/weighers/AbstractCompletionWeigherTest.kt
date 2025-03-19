// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.weighers

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.completion.test.configureByFilesWithSuffixes
import org.jetbrains.kotlin.idea.completion.test.testWithAutoCompleteSetting
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.junit.Assert

abstract class AbstractCompletionWeigherTest(val completionType: CompletionType, val relativeTestDataPath: String) :
    KotlinLightCodeInsightFixtureTestCase() {

    fun doTest(path: String) {
        val fileSuffixes = arrayOf(".Data", ".Data1", ".Data2", ".Data3", ".Data4", ".Data5", ".Data6")
        myFixture.configureByFilesWithSuffixes(dataFile(), testDataDirectory, *fileSuffixes)

        val text = myFixture.editor.document.text

        val items = InTextDirectivesUtils.findArrayWithPrefixes(text, "// ORDER:")
        Assert.assertTrue("""Some items should be defined with "// ORDER:" directive""", items.isNotEmpty())

        executeTest {
            testWithAutoCompleteSetting(text) {
                withCustomCompilerOptions(text, project, module) {
                    myFixture.complete(completionType, InTextDirectivesUtils.getPrefixedInt(text, "// INVOCATION_COUNT:") ?: 1)
                    myFixture.assertPreferredCompletionItems(InTextDirectivesUtils.getPrefixedInt(text, "// SELECTED:") ?: 0, *items)
                }
            }
        }
    }

    open fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K1, ".after") {
            test()
        }
    }
}

abstract class AbstractBasicCompletionWeigherTest : AbstractCompletionWeigherTest(CompletionType.BASIC, "weighers/basic")
abstract class AbstractSmartCompletionWeigherTest : AbstractCompletionWeigherTest(CompletionType.SMART, "weighers/smart")
