// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion.Companion.get
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings.Companion.shouldImportKotlinJpsPluginVersionFromExternalBuildSystem

class KotlinJpsPluginSettingsTest : TestCase() {
    fun `test shouldImportKotlinJpsPluginVersionFromExternalBuildSystem`() {
        try {
            shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.5.0"))
            fail("shouldImportKotlinJpsPluginVersionFromExternalBuildSystem should fail when the version is lower than " +
                         "${KotlinJpsPluginSettings.jpsMinimumSupportedVersion}")
        } catch (ignored: IllegalArgumentException) {}
        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.0-Beta")))

        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.10-Beta")))
        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.10-Beta-1234")))
        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.10-release-1234")))
        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.10-1234")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.10")))

        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.0-Beta-1234")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.0")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.10-Beta-1234")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.10")))
    }
}
