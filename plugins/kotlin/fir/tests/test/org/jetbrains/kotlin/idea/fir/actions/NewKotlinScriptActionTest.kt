// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptingHostConfiguration
import org.jetbrains.kotlin.idea.actions.isScriptLocationAccepted
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

class NewKotlinScriptActionTest : BasePlatformTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun testEmptyAcceptedLocationsAcceptsAnyDirectory() {
        val directory = myFixture.tempDirFixture.findOrCreateDir(".")
        assertTrue(isScriptLocationAccepted(testDefinition(emptyList()), directory, project))
    }

    fun testEverywhereAcceptedLocationAcceptsAnyDirectory() {
        val directory = myFixture.tempDirFixture.findOrCreateDir(".")
        assertTrue(isScriptLocationAccepted(testDefinition(listOf(ScriptAcceptedLocation.Everywhere)), directory, project))
    }

    fun testProjectAcceptedLocationAcceptsContentRootDirectory() {
        val directory = myFixture.tempDirFixture.findOrCreateDir(".")
        assertTrue(isScriptLocationAccepted(testDefinition(listOf(ScriptAcceptedLocation.Project)), directory, project))
    }

    fun testLibrariesAcceptedLocationRejectsContentRootDirectory() {
        val directory = myFixture.tempDirFixture.findOrCreateDir(".")
        assertFalse(isScriptLocationAccepted(testDefinition(listOf(ScriptAcceptedLocation.Libraries)), directory, project))
    }

    private fun testDefinition(acceptedLocations: List<ScriptAcceptedLocation>): ScriptDefinition =
        object : ScriptDefinition.FromConfigurations(
            ScriptingHostConfiguration {},
            ScriptCompilationConfiguration {
                ide.acceptedLocations(acceptedLocations)
            },
            null
        ) {}
}
