// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.WorkspaceModelTestReportParser
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.render
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.applyCommentsFrom
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorkspaceModelTestsInfrastructureTests {
    @Test
    fun testEmptyProject() {
        checkParseRenderIdentity(
            """
            MODULES
            projectName
        """.trimIndent()
        )
    }

    @Test
    fun testEmptyModule() {
        checkParseRenderIdentity(
            """
            MODULES
            foo
            
            foo.bar
        """.trimIndent()
        )
    }

    @Test
    fun testSimple() {
        checkParseRenderIdentity(
            """
            MODULES
            foo
            
            foo.bar
                content line 1
                content line 2
            
            foo.baz
                other content
            
            
            Test configuration:
            - configuration line 1
            - configuration line 2
                    
        """.trimIndent()
        )
    }

    @Test
    fun testComments() {
        checkParseRenderIdentity(
            """
            // Project comment 1
            // Project comment 2
            MODULES
            foo
            
            // Module Comment 1
            // Module Comment 2
            foo.bar
                // Content comment 1
                // Content comment 2
                content line 1
                // Content comment 3
                content line 2
            
            foo.baz
                // Content comment 4
                other content
            
            
            Test configuration:
            - configuration line 1
            - configuration line 2
                    
        """.trimIndent()
        )
    }

    @Test
    fun testProjectComments() {
        // add comment
        checkCommentsApplication(
            donorText = """
                // Project Comment
                MODULES
            """.trimIndent(),

            recipientText = """
                MODULES
            """.trimIndent(),

            expectedText = """
                // Project Comment
                MODULES
            """.trimIndent()
        )

        // remove comment
        checkCommentsApplication(
            donorText = """
                MODULES
            """.trimIndent(),

            recipientText = """
                // project comment
                MODULES
            """.trimIndent(),

            expectedText = """
                MODULES
            """.trimIndent()
        )
    }

    @Test
    fun testModuleCommentAddition() {
        // add comment + some modules were added, some removed, causing reorderings
        checkCommentsApplication(
            donorText = """
                MODULES
                a
                
                // Module comment
                foo
            """.trimIndent(),

            recipientText = """
                MODULES
                baz
                
                bar
                
                foo
            """.trimIndent(),

            expectedText = """
                MODULES
                bar
                
                baz
                
                // Module comment
                foo
            """.trimIndent()
        )

        // remove comment
        checkCommentsApplication(
            donorText = """
                MODULES
                foo
            """.trimIndent(),

            recipientText = """
                MODULES
                // Module comment
                foo
            """.trimIndent(),

            expectedText = """
                MODULES
                foo
            """.trimIndent()
        )

        // module with the comment has been removed/renamed
        checkCommentsApplication(
            donorText = """
                MODULES
                // Foo comment
                foo
                
                // Bar comment
                bar
            """.trimIndent(),

            recipientText = """
                MODULES
                foo
                
                bar-renamed
            """.trimIndent(),

            expectedText = """
                MODULES
                bar-renamed
                
                // Foo comment
                foo
            """.trimIndent()
        )
    }

    @Test
    fun testModuleDataCommentAddition() {
        // add comment + some datas were added, some removed, causing reorderings
        checkCommentsApplication(
            donorText = """
                MODULES
                foo
                    // Module data 3 comment
                    // Module data 3 comment
                    module-data-3
                    module-data-4
            """.trimIndent(),

            recipientText = """
                MODULES
                foo
                    module-data-1
                    module-data-2
                    module-data-3
            """.trimIndent(),

            expectedText = """
                MODULES
                foo
                    module-data-1
                    module-data-2
                    // Module data 3 comment
                    // Module data 3 comment
                    module-data-3
            """.trimIndent()
        )

        // remove comment
        checkCommentsApplication(
            donorText = """
                MODULES
                foo
                    data-1
            """.trimIndent(),

            recipientText = """
                MODULES
                foo
                    // comment
                    // comment
                    data-1
            """.trimIndent(),

            expectedText = """
                MODULES
                foo
                    data-1
            """.trimIndent()
        )

        // data with the comment has been removed/renamed
        checkCommentsApplication(
            donorText = """
                MODULES
                foo
                    // Module data 3 comment
                    // Module data 3 comment
                    module-data-3
                    // Module data 4 comment
                    module-data-4
            """.trimIndent(),

            recipientText = """
                MODULES
                foo
                    module-data-4-renamed
                    module-data-3
            """.trimIndent(),

            expectedText = """
                MODULES
                foo
                    // Module data 3 comment
                    // Module data 3 comment
                    module-data-3
                    module-data-4-renamed
            """.trimIndent()
        )
    }


    private fun checkParseRenderIdentity(text: String) {
        val cleanedUpText = text.trim()
        val parsed = WorkspaceModelTestReportParser.parse(cleanedUpText)
        val rendered = parsed.render(respectOrder = false).trim()
        assertEquals(cleanedUpText, rendered, "Expected rendered text to be equal to the parsed one")
    }

    private fun checkCommentsApplication(donorText: String, recipientText: String, expectedText: String) {
        val parsedRecipient = WorkspaceModelTestReportParser.parse(recipientText.trim())
        val parsedDonor = WorkspaceModelTestReportParser.parse(donorText.trim())

        val resultingReport = parsedRecipient.applyCommentsFrom(parsedDonor)
        val actualText = resultingReport.render(respectOrder = false).trim()

        assertEquals(actualText, expectedText)
    }
}
