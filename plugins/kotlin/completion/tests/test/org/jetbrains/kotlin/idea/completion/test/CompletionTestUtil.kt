// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.test.KotlinRoot
import org.junit.Assert

@JvmField
val COMPLETION_TEST_DATA_BASE = KotlinRoot.DIR.resolve("completion/tests/testData")

fun testCompletion(
    fileText: String,
    platform: TargetPlatform?,
    complete: (CompletionType, Int) -> Array<LookupElement>?,
    defaultCompletionType: CompletionType = CompletionType.BASIC,
    defaultInvocationCount: Int = 0,
    ignoreProperties: Collection<String> = emptyList(),
    additionalValidDirectives: Collection<String> = emptyList()
) {
    testWithAutoCompleteSetting(fileText) {
        val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType
        val invocationCount = ExpectedCompletionUtils.getInvocationCount(fileText) ?: defaultInvocationCount
        val items = complete(completionType, invocationCount) ?: emptyArray()

        ExpectedCompletionUtils.assertDirectivesValid(fileText, additionalValidDirectives)

        val expected = ExpectedCompletionUtils.itemsShouldExist(fileText, platform)
        val unexpected = ExpectedCompletionUtils.itemsShouldAbsent(fileText, platform)
        val itemsNumber = ExpectedCompletionUtils.getExpectedNumber(fileText, platform)
        val nothingElse = ExpectedCompletionUtils.isNothingElseExpected(fileText)

        Assert.assertTrue(
            "Should be some assertions about completion",
            expected.size != 0 || unexpected.size != 0 || itemsNumber != null || nothingElse
        )
        ExpectedCompletionUtils.assertContainsRenderedItems(expected, items, ExpectedCompletionUtils.isWithOrder(fileText), nothingElse, ignoreProperties)
        ExpectedCompletionUtils.assertNotContainsRenderedItems(unexpected, items, ignoreProperties)

        if (itemsNumber != null) {
            val expectedItems = ExpectedCompletionUtils.listToString(ExpectedCompletionUtils.getItemsInformation(items))
            Assert.assertEquals("Invalid number of completion items: ${expectedItems}", itemsNumber, items.size)
        }
    }
}

private fun testWithAutoCompleteSetting(fileText: String, doTest: () -> Unit) {
    val autoComplete = ExpectedCompletionUtils.getAutocompleteSetting(fileText) ?: false

    val settings = CodeInsightSettings.getInstance()
    val oldValue1 = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
    val oldValue2 = settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION
    try {
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
        settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = autoComplete
        doTest()
    } finally {
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = oldValue1
        settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = oldValue2
    }
}

internal fun JavaCodeInsightTestFixture.addCharacterCodingException() {
    addClass(
        """
        package java.nio.charset;
                                
        import java.io.IOException;            
        
        public class CharacterCodingException extends IOException {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addAppendable() {
    addClass(
        """
        package java.lang;
        
        import java.io.IOException;
        
        public interface Appendable {
            Appendable append(CharSequence csq) throws IOException;
            Appendable append(CharSequence csq, int start, int end) throws IOException;
            Appendable append(char c) throws IOException;
        }
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addHashSet() {
    addClass(
        """
        package java.util;
        
        import java.io.Serializable;
        
        public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, Serializable {
            @Override
            public Iterator<E> iterator() {
                return null;
            }
                    
            @Override
            public int size() {
                return 0;
            }
        }
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addLinkedHashSet() {
    addClass(
        """
        package java.util;
        
        import java.io.Serializable;
                    
        public class LinkedHashSet<E> extends HashSet<E> implements Set<E>, Cloneable, Serializable {}
        """.trimIndent()
    )
}