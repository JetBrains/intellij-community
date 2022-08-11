// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class ImportKotlinGradlePluginVersionTest : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    @PluginTargetVersions
    fun testImportKotlinGradlePluginVersion() {
        configureByFiles()
        val builtGradleModel = buildKotlinMPPGradleModel()
        val model = builtGradleModel.getNotNullByProjectPathOrThrow(":")

        assertEquals(kotlinPluginVersion, model.kotlinGradlePluginVersion?.toKotlinToolingVersion())
        val kotlinGradlePluginVersion = model.kotlinGradlePluginVersion!!

        /* Just check if those calls make it through classLoader boundaries */
        assertEquals(0, kotlinGradlePluginVersion.compareTo(kotlinPluginVersion))
        assertEquals(0, kotlinGradlePluginVersion.compareTo(kotlinPluginVersion.toString()))
        assertEquals(0, kotlinGradlePluginVersion.compareTo(KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion.versionString)!!))

        assertNotNull(kotlinGradlePluginVersion.invokeWhenAtLeast(kotlinPluginVersion) { })
        assertNotNull(kotlinGradlePluginVersion.invokeWhenAtLeast(kotlinPluginVersion.toString()) {})
        assertNotNull(
            kotlinGradlePluginVersion.invokeWhenAtLeast(KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion.versionString)!!) {}
        )

        assertNotSame(kotlinGradlePluginVersion, kotlinGradlePluginVersion.reparse()!!)
        assertEquals(kotlinGradlePluginVersion.reparse(), kotlinGradlePluginVersion.reparse()!!)
    }
}
