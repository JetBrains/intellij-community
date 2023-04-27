// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.newTests.TestFeatureWithSetUpTearDown
import org.jetbrains.kotlin.idea.codeInsight.gradle.ImportStatusCollector
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

internal object NoErrorEventsDuringImportFeature : TestFeatureWithSetUpTearDown<Unit> {
    private var importStatusCollector: ImportStatusCollector? = null

    override fun createDefaultConfiguration() { }

    override fun additionalSetUp() {
        importStatusCollector = ImportStatusCollector()
        ExternalSystemProgressNotificationManager
            .getInstance()
            .addNotificationListener(importStatusCollector!!)
    }

    override fun additionalTearDown() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .removeNotificationListener(importStatusCollector!!)
    }

    override fun KotlinMppTestsContext.afterImport() {
        checkImportErrors(testDataDirectory)
    }

    fun checkImportErrors(testDataDirectory: File) {
        val expectedFailure = File(testDataDirectory, "importErrors.txt")
        val buildErrors = importStatusCollector!!.buildErrors
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
