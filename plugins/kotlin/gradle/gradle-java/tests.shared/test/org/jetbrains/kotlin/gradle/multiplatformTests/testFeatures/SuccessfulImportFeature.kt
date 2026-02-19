// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeatureWithSetUpTearDown
import org.jetbrains.kotlin.idea.codeInsight.gradle.ImportStatusCollector


object SuccessfulImportFeature : TestFeatureWithSetUpTearDown<Unit> {
    private var importStatusCollector: ImportStatusCollector? = null
    override fun createDefaultConfiguration() {}
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

    override fun KotlinSyncTestsContext.afterImport() {
        val isBuildSuccessful = importStatusCollector!!.isBuildSuccessful
        if (!isBuildSuccessful) {
            error("BUILD FAILED appeared during the import")
        }
    }
}