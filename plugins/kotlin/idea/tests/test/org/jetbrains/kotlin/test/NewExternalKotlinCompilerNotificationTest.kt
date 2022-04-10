// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test

import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase.assertEquals
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.notifications.*
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NewExternalKotlinCompilerNotificationTest : LightPlatformTestCase() {
    fun testWhatIsNewPage() {
        assertWhatIsNewPage(kotlinVersion = "1.6.21", expectedPageVersion = "1620", expectedCampaignVersion = "1-6-21")
        assertWhatIsNewPage(kotlinVersion = "1.6.20", expectedPageVersion = "1620", expectedCampaignVersion = "1-6-20")
        assertWhatIsNewPage(kotlinVersion = "1.6.11", expectedPageVersion = "16", expectedCampaignVersion = "1-6-11")
        assertWhatIsNewPage(kotlinVersion = "1.6.0", expectedPageVersion = "16", expectedCampaignVersion = "1-6-0")
        assertWhatIsNewPage(kotlinVersion = "1.5.32", expectedPageVersion = "1530", expectedCampaignVersion = "1-5-32")
        assertWhatIsNewPage(kotlinVersion = "1.5.31", expectedPageVersion = "1530", expectedCampaignVersion = "1-5-31")
        assertWhatIsNewPage(kotlinVersion = "1.5.30", expectedPageVersion = "1530", expectedCampaignVersion = "1-5-30")
        assertWhatIsNewPage(kotlinVersion = "1.5.20", expectedPageVersion = "1520", expectedCampaignVersion = "1-5-20")
        assertWhatIsNewPage(kotlinVersion = "1.5.10", expectedPageVersion = "15", expectedCampaignVersion = "1-5-10")
        assertWhatIsNewPage(kotlinVersion = "1.5.0", expectedPageVersion = "15", expectedCampaignVersion = "1-5-0")
    }

    fun testDown() {
        assertDowngradedVersion(kotlinVersion = "1.6.21", downgradedVersion = "1.6.20")
        assertDowngradedVersion(kotlinVersion = "1.6.20", downgradedVersion = "1.6.20")
        assertDowngradedVersion(kotlinVersion = "1.6.11", downgradedVersion = "1.6.0")
        assertDowngradedVersion(kotlinVersion = "1.6.10", downgradedVersion = "1.6.0")
        assertDowngradedVersion(kotlinVersion = "1.6.0", downgradedVersion = "1.6.0")
        assertDowngradedVersion(kotlinVersion = "1.5.32", downgradedVersion = "1.5.30")
        assertDowngradedVersion(kotlinVersion = "1.5.31", downgradedVersion = "1.5.30")
        assertDowngradedVersion(kotlinVersion = "1.5.30", downgradedVersion = "1.5.30")
        assertDowngradedVersion(kotlinVersion = "1.5.20", downgradedVersion = "1.5.20")
        assertDowngradedVersion(kotlinVersion = "1.5.10", downgradedVersion = "1.5.0")
        assertDowngradedVersion(kotlinVersion = "1.5.0", downgradedVersion = "1.5.0")
    }

    fun testNotificationsWithoutLastBundled() {
        assertLastBundled(expected = null)
        assertNotification(bundledVersion = "1.6.21", externalVersion = "1.6.20", notify = false)
        assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.20", notify = false)

        assertNotification(bundledVersion = "1.6.21", externalVersion = "1.6.10", notify = true)
        assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.10", notify = true)
        assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.10", notify = true)
        assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.0", notify = true)

        assertNotification(bundledVersion = "1.6.11", externalVersion = "1.6.10", notify = false)
        assertNotification(bundledVersion = "1.6.10", externalVersion = "1.6.10", notify = false)
        assertNotification(bundledVersion = "1.6.10", externalVersion = "1.6.0", notify = false)

        assertNotification(bundledVersion = "1.6.0", externalVersion = "1.6.20", notify = false)
        assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.30", notify = false)
    }

    fun testNotificationsWithLastBundled() {
        for (version in listOf("1.6.0", "1.6.10", "1.6.11")) {
            setLastBundled(version)
            assertNotification(bundledVersion = "1.6.0", externalVersion = "1.5.32", notify = false)
            assertNotification(bundledVersion = "1.6.10", externalVersion = "1.5.32", notify = false)
            assertNotification(bundledVersion = "1.6.11", externalVersion = "1.5.32", notify = false)
            assertNotification(bundledVersion = "1.6.20", externalVersion = "1.5.32", notify = true)
            assertNotification(bundledVersion = "1.6.21", externalVersion = "1.5.32", notify = true)
            assertLastBundled(version)
        }

        for (version in listOf("1.6.20", "1.6.21", "1.6.22")) {
            setLastBundled(version)
            assertNotification(bundledVersion = "1.6.20", externalVersion = "1.6.20", notify = false)
            assertNotification(bundledVersion = "1.6.10", externalVersion = "1.5.32", notify = false)
            assertNotification(bundledVersion = "1.6.20", externalVersion = "1.5.32", notify = false)
            assertNotification(bundledVersion = "1.6.30", externalVersion = "1.5.32", notify = true)
            assertNotification(bundledVersion = "1.6.31", externalVersion = "1.6.22", notify = true)
            assertLastBundled(version)
        }

        dropLastBundled()
    }
}

private fun dropLastBundled() {
    PropertiesComponent.getInstance().unsetValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME)
}

private fun setLastBundled(version: String) {
    PropertiesComponent.getInstance().setValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME, version)
}

private fun assertNotification(bundledVersion: String, externalVersion: String, notify: Boolean) {
    assertEquals(
        notify,
        newExternalKotlinCompilerShouldBePromoted(
            bundledCompilerVersion = bundledVersion.toKotlinVersion(),
            externalCompilerVersion = { externalVersion.toKotlinVersion() },
        ),
    )
}

private fun assertLastBundled(expected: String?) {
    assertEquals(expected, PropertiesComponent.getInstance().getValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME))
}

private fun assertWhatIsNewPage(kotlinVersion: String, expectedPageVersion: String, expectedCampaignVersion: String) {
    val kotlinPlainVersion = kotlinVersion.toKotlinVersion()
    assertEquals(expectedPageVersion, kotlinPlainVersion.whatIsNewPageVersion)
    assertEquals(expectedCampaignVersion, kotlinPlainVersion.campaignVersion)
}

private fun assertDowngradedVersion(kotlinVersion: String, downgradedVersion: String) {
    val kotlinPlainVersion = kotlinVersion.toKotlinVersion()
    assertEquals(downgradedVersion, kotlinPlainVersion.dropHotfixPart.toString())
}

private fun String.toKotlinVersion(): KotlinVersion = IdeKotlinVersion.opt(this)?.kotlinVersion ?: error("'$this' can't be parsed")
