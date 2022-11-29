// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.ImportStatusCollector
import org.junit.rules.ExternalResource

class NoErrorEventsDuringImportService : ExternalResource() {
    private val importStatusCollector = ImportStatusCollector()

    override fun before() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .addNotificationListener(importStatusCollector)
    }

    override fun after() {
        assertNoBuildErrorEventsReported()

        ExternalSystemProgressNotificationManager
            .getInstance()
            .removeNotificationListener(importStatusCollector)
    }

    private fun assertNoBuildErrorEventsReported() {
        UsefulTestCase.assertEmpty("No error events was expected to be reported", importStatusCollector.buildErrors)
    }

}
