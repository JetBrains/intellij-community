// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.externsions

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.jps.model.java.JdkVersionDetector.Variant
import org.jetbrains.kotlin.idea.gradle.extensions.nameSupportedByFoojayPlugin
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@TestApplication
class VariantExtensionsTest {

    @Test
    fun testVariantNameSupportedByFoojayPlugin() {
        Variant.entries.forEach { variant ->
            assertEquals(variant.expectedNameSupportedByFoojayPlugin, variant.nameSupportedByFoojayPlugin)
        }
    }

    private val Variant.expectedNameSupportedByFoojayPlugin: String?
        get() = when (this) {
            Variant.AdoptOpenJdk_HS -> "AOJ"
            Variant.AdoptOpenJdk_J9 -> "AOJ OpenJ9"
            Variant.IBM, Variant.Semeru -> "Semeru"
            Variant.JBR -> "JetBrains"
            Variant.BiSheng -> "BiSheng"
            Variant.Corretto -> "Corretto"
            Variant.Dragonwell -> "Dragonwell"
            Variant.GraalVM -> "GraalVM"
            Variant.Kona -> "Kona"
            Variant.Liberica -> "Liberica"
            Variant.Microsoft -> "Microsoft"
            Variant.Oracle -> "Oracle"
            Variant.SapMachine -> "SapMachine"
            Variant.Temurin -> "Temurin"
            Variant.Zulu -> "Zulu"
            Variant.Unknown, Variant.GraalVMCE, Variant.Homebrew -> null
        }
}