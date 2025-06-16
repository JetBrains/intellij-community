// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.Assert

@JvmField
val COMPLETION_TEST_DATA_BASE = KotlinRoot.DIR.resolve("completion/testData")

fun testCompletion(
    fileText: String,
    platform: TargetPlatform?,
    complete: (CompletionType, Int) -> Array<LookupElement>?,
    defaultCompletionType: CompletionType = CompletionType.BASIC,
    defaultInvocationCount: Int = 0,
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
        ExpectedCompletionUtils.assertContainsRenderedItems(expected, items, ExpectedCompletionUtils.isWithOrder(fileText), nothingElse)
        ExpectedCompletionUtils.assertNotContainsRenderedItems(unexpected, items)

        if (itemsNumber != null) {
            val expectedItems = ExpectedCompletionUtils.listToString(ExpectedCompletionUtils.getItemsInformation(items))
            Assert.assertEquals("Invalid number of completion items: ${expectedItems}", itemsNumber, items.size)
        }
    }
}

fun testWithAutoCompleteSetting(fileText: String, doTest: () -> Unit) {
    val autoComplete = ExpectedCompletionUtils.getAutocompleteSetting(fileText) ?: false

    CodeInsightSettings.runWithTemporarySettings<_,Error> { settings ->
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
        settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = autoComplete
        doTest()
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

internal fun JavaCodeInsightTestFixture.addNoSuchAlgorithmException() {
    addClass(
        """
        package java.security;
        
        public class NoSuchAlgorithmException extends Exception {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addUrlConnection() {
    addClass(
        """
        package java.net;
        
        public class URLConnection {
          public URL getURL() { return null; }
        }
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSocket() {
    addClass(
        """
        package java.net;
        
        import java.io.InputStream;
        
        public class Socket {
          public InputStream getInputStream() {
            return null;
          }
        }
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addUnknownHostException() {
    addClass(
        """
        package java.net;
        
        public class UnknownHostException extends Exception {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSqlDate() {
    addClass(
        """
        package java.sql;
        
        public class Date {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSqlBlob() {
    addClass(
        """
        package java.sql;
        
        public class Blob {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSqlArray() {
    addClass(
        """
        package java.sql;
        
        public class Array {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSqlStatement() {
    addClass(
        """
        package java.sql;
        
        public class Statement {}
        """.trimIndent()
    )
}

internal fun JavaCodeInsightTestFixture.addSwingUtilities() {
    addClass(
        """
        package javax.swing;
        
        public class SwingUtilities {
          static public void invokeLater(Runnable doRun) {}
          static public void invokeAndWait(Runnable doRun) {}
          
          static void installSwingDropTargetAsNecessary() {}
        }
        """.trimIndent()
    )
}
