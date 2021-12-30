// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.configuration.notifications.campaignVersion
import org.jetbrains.kotlin.idea.configuration.notifications.whatIsNewPageVersion
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class WhatIsNewUrlTest : UsefulTestCase() {
    fun test() {
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

    private fun assertWhatIsNewPage(kotlinVersion: String, expectedPageVersion: String, expectedCampaignVersion: String) {
        val kotlinPlainVersion = KotlinVersionVerbose.parse(kotlinVersion)?.plainVersion ?: error("'$kotlinVersion' can't be parsed")
        assertEquals(expectedPageVersion, kotlinPlainVersion.whatIsNewPageVersion)
        assertEquals(expectedCampaignVersion, kotlinPlainVersion.campaignVersion)
    }
}
