// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.awt.datatransfer.StringSelection

class KDocCopyPastePreProcessorTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testDefaultPaste() = doTypeTest(
        """
            Hello
            World
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteRightAfterAsterisks() = doTypeTest(
        """
            Hello
            World
        """.trimIndent(),
        """
            /**
             *<caret>
             */
        """.trimIndent(),
        """
            /**
             *Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteNotAlignedText() = doTypeTest(
        """
            Hello
                 World
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * Hello
             *      World
             */
        """.trimIndent()
    )

    fun testPasteFirstLineNotAlignedText() = doTypeTest(
        """
                  Hello
            World
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             *       Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteTextWithTrailingEmptyLines() = doTypeTest(
        """
            Hello
            World
            
            
            
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteTextWithLeadingEmptyLines() = doTypeTest(
        """
            
            
            Hello
            World
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteTextWithLeadingAndTrailingEmptyLines() = doTypeTest(
        """
            
            
            Hello
            World
            
            
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * Hello
             * World
             */
        """.trimIndent()
    )

    fun testPasteKotlinCode() = doTypeTest(
        """
            fun main(args: Array<String>) {
                println("Hello, world")
            }
        """.trimIndent(),
        """
            /**
             * Example usage:
             * 
             * ```
             * <caret>
             * ```
             */
        """.trimIndent(),
        """
            /**
             * Example usage:
             * 
             * ```
             * fun main(args: Array<String>) {
             *     println("Hello, world")
             * }
             * ```
             */
        """.trimIndent()
    )

    fun testPasteComplexMarkdownCode() = doTypeTest(
        """
            # Heading
            
            Some long
            text.
            
            1. Item 1
            2. Item 2
            
            ```kotlin
            fun main(args: Array<String>) {
                println("Hello, world")
            }
            ```
            
            > Quote
            
            | id | name |
            |----|------|
            | 1  | John |
            | 2  | Jack |
        """.trimIndent(),
        """
            /**
             * <caret>
             */
        """.trimIndent(),
        """
            /**
             * # Heading
             *
             * Some long
             * text.
             *
             * 1. Item 1
             * 2. Item 2
             *
             * ```kotlin
             * fun main(args: Array<String>) {
             *     println("Hello, world")
             * }
             * ```
             *
             * > Quote
             *
             * | id | name |
             * |----|------|
             * | 1  | John |
             * | 2  | Jack |
             */
        """.trimIndent()
    )

    fun testNonDocComment() = doTypeTest(
        """
            Hello
            World
        """.trimIndent(),
        """
            /*
             * <caret>
             */
        """.trimIndent(),
        """
            /*
             * Hello
            World
             */
        """.trimIndent()
    )

    private fun doTypeTest(text: String, beforeText: String, afterText: String) {
        myFixture.configureByText("a.kt", beforeText.trimMargin())
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        EditorTestUtil.performPaste(myFixture.editor)
        myFixture.checkResult(afterText.trimMargin())
    }
}
