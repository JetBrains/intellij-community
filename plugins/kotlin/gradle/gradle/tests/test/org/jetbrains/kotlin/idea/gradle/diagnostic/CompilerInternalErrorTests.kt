// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.diagnostic

import org.testng.annotations.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerInternalErrorTests {
    @Test
    fun `parse file with simple daemon compilation error `() {
        var wasCalled = false
        val expectedMessage = "Daemon compilation failed: null: daemonCompilationFail"
        val causeMessage = "java.lang.Exception"

        readErrorFileAndProcessEvent(File("resources/errors-daemonCompilationFail.log")) { kotlinCompilerCrash, _ ->
            wasCalled = true
            assertEquals(
                expectedMessage,
                kotlinCompilerCrash.message,
                "expected message: \n$expectedMessage\n but got \n${kotlinCompilerCrash.message}"
            )
            assertEquals(causeMessage, kotlinCompilerCrash.cause!!.message,
            "expected cause message: \n$causeMessage but got \n${kotlinCompilerCrash.cause?.message}")
            assertEquals("Unknown", kotlinCompilerCrash.version,
                         "expected kotlin version: \n Unknown but got \n${kotlinCompilerCrash.version}")
        }
        assertTrue { wasCalled }
    }

    @Test
    fun `parse file with compilation error with attachment`() {
        var wasCalled = false
        val expectedMessage = "org.jetbrains.kotlin.util.KotlinFrontEndException: Exception while analyzing expression in (5,13) in " +
                "/incrementalMultiproject/lib/src/main/kotlin/any.kt: kotlinExceptionWithAttachments"
        val firstAttachmentMessage = "causeThrowable " +
                "java.lang.AssertionError: Recursion detected on input: CityBuilder under LockBasedStorageManager@3c7d6839 (TopDownAnalyzer for JVM)"
        val secondAttachmentMessage = "expression.kt " +
                "City.builder().record(\"Fontanka\", 76).build()"
        readErrorFileAndProcessEvent(File("resources/errors-kotlinExceptionWithAttachments.log")) { kotlinCompilerCrash, _ ->
            wasCalled = true
            assertEquals(
                expectedMessage,
                kotlinCompilerCrash.message,
                "expected message prefix: \n$expectedMessage\n but got \n${kotlinCompilerCrash.message}"
            )
            assertContains(listOf(firstAttachmentMessage, secondAttachmentMessage), kotlinCompilerCrash.cause!!.message)
        }
        assertTrue { wasCalled }
    }

    @Test
    fun `parse file with kotlin version`() {
        var wasCalled = false
        val kotlinVersion = "1.8.255-SNAPSHOT"
        readErrorFileAndProcessEvent(File("resources/errors-kotlinVersion.log")) { kotlinCompilerCrash, _ ->
            wasCalled = true
            assertEquals(kotlinVersion, kotlinCompilerCrash.version,
                         "expected kotlin version: \n$kotlinVersion but got \n${kotlinCompilerCrash.version}")
        }
        assertTrue { wasCalled }
    }

}