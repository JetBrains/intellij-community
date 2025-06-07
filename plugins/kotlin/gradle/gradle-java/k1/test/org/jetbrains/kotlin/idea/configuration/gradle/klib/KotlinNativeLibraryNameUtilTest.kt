// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration.gradle.klib

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfo
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.isGradleLibraryName
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.parseIDELibraryName
import org.jetbrains.kotlin.idea.gradleJava.configuration.klib.ideName
import java.io.File

class KotlinNativeLibraryNameUtilTest : TestCase() {
    fun testBuildIDELibraryName() {
        assertEquals(
            "Kotlin/Native 1.5.20 - foo | [(a, b)]",
            KlibInfo(
                path = File(""),
                sourcePaths = emptyList(),
                libraryName = "foo",
                isCommonized = true,
                isStdlib = false,
                isFromNativeDistribution = true,
                targets = KlibInfo.NativeTargets.CommonizerIdentity("(a, b)")
            ).ideName(IdeKotlinVersion.get("1.5.20"))
        )

        assertEquals(
            "foo | [(a, b)]",
            KlibInfo(
                path = File(""),
                sourcePaths = emptyList(),
                libraryName = "foo",
                isCommonized = true,
                isStdlib = false,
                isFromNativeDistribution = false,
                targets = KlibInfo.NativeTargets.CommonizerIdentity("(a, b)")
            ).ideName(null)
        )

        assertEquals(
            "Kotlin/Native 1.5.20 - foo",
            KlibInfo(
                path = File(""),
                sourcePaths = emptyList(),
                libraryName = "foo",
                isCommonized = false,
                isStdlib = true,
                isFromNativeDistribution = true,
                targets = KlibInfo.NativeTargets.CommonizerIdentity("(a, b)")
            ).ideName(IdeKotlinVersion.get("1.5.20"))
        )

        assertEquals(
            "Kotlin/Native foo",
            KlibInfo(
                path = File(""),
                sourcePaths = emptyList(),
                libraryName = "foo",
                isCommonized = true,
                isStdlib = false,
                isFromNativeDistribution = true,
                targets = null
            ).ideName(null)
        )

    }

    fun testParseIDELibraryName() {
        assertEquals(
            Triple("1.3.60", "stdlib", null),
            parseIDELibraryName("Kotlin/Native 1.3.60 - stdlib")
        )

        assertEquals(
            Triple("1.3.60-eap-23", "Accelerate", "macos_x64"),
            parseIDELibraryName("Kotlin/Native 1.3.60-eap-23 - Accelerate [macos_x64]")
        )

        assertEquals(
            Triple("1.3.60-eap-23", "Accelerate", "ios_arm64, ios_x64"),
            parseIDELibraryName("Kotlin/Native 1.3.60-eap-23 - Accelerate [ios_arm64, ios_x64]")
        )

        assertEquals(
            Triple("1.3.60-eap-23", "Accelerate", "ios_arm64(*), ios_x64"),
            parseIDELibraryName("Kotlin/Native 1.3.60-eap-23 - Accelerate [ios_arm64(*), ios_x64]")
        )

        assertNull(parseIDELibraryName("Kotlin/Native - something unexpected"))

        assertNull(parseIDELibraryName("foo.klib"))

        assertNull(parseIDELibraryName("Gradle: some:third-party-library:1.2"))
    }

    fun testIsGradleLibraryName() {
        assertFalse(isGradleLibraryName("Kotlin/Native 1.3.60 - stdlib"))

        assertFalse(isGradleLibraryName("Kotlin/Native 1.3.60-eap-23 - Accelerate [macos_x64]"))

        assertFalse(isGradleLibraryName("foo.klib"))

        assertTrue(isGradleLibraryName("Gradle: some:third-party-library:1.2"))
    }
}
