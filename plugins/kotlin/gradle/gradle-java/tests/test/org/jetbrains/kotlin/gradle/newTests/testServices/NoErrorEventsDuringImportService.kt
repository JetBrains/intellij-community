// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.jetbrains.kotlin.idea.codeInsight.gradle.ImportStatusCollector
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.rules.ExternalResource
import java.io.File

class NoErrorEventsDuringImportService : ExternalResource() {
    private val importStatusCollector = ImportStatusCollector()

    override fun before() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .addNotificationListener(importStatusCollector)
    }

    override fun after() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .removeNotificationListener(importStatusCollector)
    }

    fun checkImportErrors(testDataDirectoryService: TestDataDirectoryService) {
        val expectedFailure = File(testDataDirectoryService.testDataDirectory(), "importErrors.txt")
        val buildErrors = importStatusCollector.buildErrors
        when {
            !expectedFailure.exists() && buildErrors.isEmpty() -> return

            expectedFailure.exists() && buildErrors.isEmpty() ->
                error(
                    "Expected to have some import errors, but none were actually reported\n" +
                            "If that's the expected behaviour, remove the following file: \n" +
                            expectedFailure.canonicalPath
                )

            else -> {
                // assertFileEquals will handle both remaining cases:
                // - expectedFailure exists and some errors reported (will check that the failure is the same)
                // - expectedFailure doesn't exist, but some errors reported (will create the expected file + fail the test once)
                val buildErrorsString = buildErrors.joinToString(separator = "\n") { it.message }
                KotlinTestUtils.assertEqualsToFile(expectedFailure, buildErrorsString)
            }
        }
    }

}
