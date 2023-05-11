// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.weighers

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.completion.test.configureByFilesWithSuffixes
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
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
            withCustomCompilerOptions(text, project, module) {
                myFixture.complete(completionType, InTextDirectivesUtils.getPrefixedInt(text, "// INVOCATION_COUNT:") ?: 1)
                myFixture.assertPreferredCompletionItems(InTextDirectivesUtils.getPrefixedInt(text, "// SELECTED:") ?: 0, *items)
            }
        }
    }

    open fun executeTest(test: () -> Unit) {
        test()
    }
}

abstract class AbstractBasicCompletionWeigherTest : AbstractCompletionWeigherTest(CompletionType.BASIC, "weighers/basic")
abstract class AbstractSmartCompletionWeigherTest : AbstractCompletionWeigherTest(CompletionType.SMART, "weighers/smart")
