// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.WorkspaceModelTestReportParser
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.render
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorkspaceTestDataParsingUnitTests {
    @Test
    fun testEmptyProject() {
        checkParseRenderIdentity("""
            MODULES
            projectName
        """.trimIndent())
    }

    @Test
    fun testEmptyModule() {
        checkParseRenderIdentity("""
            MODULES
            foo
            
            foo.bar
        """.trimIndent())
    }

    @Test
    fun testSimple() {
        checkParseRenderIdentity("""
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
                    
        """.trimIndent())
    }

    @Test
    fun testComments() {
        checkParseRenderIdentity("""
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
                    
        """.trimIndent())
    }

    private fun checkParseRenderIdentity(text: String) {
        val cleanedUpText = text.trim()
        val parsed = WorkspaceModelTestReportParser.parse(cleanedUpText)
        val rendered = parsed.render(respectOrder = false).trim()
        assertEquals(cleanedUpText, rendered, "Expected rendered text to be equal to the parsed one")
    }
}
