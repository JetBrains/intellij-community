// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration.gradle.klib

import com.intellij.testFramework.PlatformTestUtil.getTestName
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfo
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfo.NativeTargets.CommonizerIdentity
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KlibInfoProvider
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.konan.library.*
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class KlibInfoProviderTest : TestCase() {
    fun testOldStyleKlibsFromNativeDistributionRecognized() = doTest(
        ::generateExpectedKlibsFromDistribution
    )

    fun testKlibsFromNativeDistributionWithSingleComponentRecognized() = doTest(
        ::generateExpectedKlibsFromDistribution
    )

    fun testKlibsFromNativeDistributionWithMultipleComponentsRecognized() = doTest(
        ::generateExpectedKlibsFromDistribution
    )

    fun testCommonizedKlibsFromNativeDistributionRecognized() = doTest(
        ::generateExpectedKlibsFromDistribution,
        ::generateExpectedCommonizedKlibsFromDistribution
    )

    private fun doTest(vararg expectedKlibsGenerators: (kotlinNativeHome: File) -> Map<File, KlibInfo>) {
        val kotlinNativeHome = testDataDir.resolve(getTestName(name, true)).resolve("kotlin-native-PLATFORM-VERSION")
        val sourcesDir = kotlinNativeHome.resolve(KONAN_DISTRIBUTION_SOURCES_DIR)

        val klibProvider = KlibInfoProvider.create(kotlinNativeHome = kotlinNativeHome)

        val potentialKlibPaths = mutableListOf<File>()
        potentialKlibPaths += externalLibsDir.children()

        with(kotlinNativeHome.resolve(KONAN_DISTRIBUTION_KLIB_DIR)) {
            potentialKlibPaths += resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR).children()
            potentialKlibPaths += resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR, "macos_x64").children()

            with(resolve(KONAN_DISTRIBUTION_COMMONIZED_LIBS_DIR, "ios_arm64-ios_x64-discriminator")) {
                potentialKlibPaths += resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR).children()
                potentialKlibPaths += resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR, "ios_arm64").children()
                potentialKlibPaths += resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR, "ios_x64").children()
            }
        }

        val actualKlibs = potentialKlibPaths.mapNotNull { klibProvider.getKlibInfo(it) }
            .associateBy { it.path.relativeTo(kotlinNativeHome) }
            .filter { it.value.isFromNativeDistribution }

        val expectedKlibsFromDistribution = mutableMapOf<File, KlibInfo>().apply {
            expectedKlibsGenerators.forEach { this += it(kotlinNativeHome) }
        }

        assertEquals(expectedKlibsFromDistribution.keys, actualKlibs.keys)
        for (klibPath in actualKlibs.keys) {
            val actualKlib = actualKlibs.getValue(klibPath)
            val expectedKlib = expectedKlibsFromDistribution.getValue(klibPath)

            assertEquals(expectedKlib::class.java, actualKlib::class.java)

            assertEquals(expectedKlib.path, actualKlib.path)
            assertEquals(expectedKlib.libraryName, actualKlib.libraryName)

            val actualSources = actualKlib.sourcePaths.map { it.relativeTo(sourcesDir) }.toSet()
            val expectedSources = expectedKlib.sourcePaths.map { it.relativeTo(sourcesDir) }.toSet()

            assertEquals(expectedSources, actualSources)
            assertEquals(expectedKlib.isCommonized, actualKlib.isCommonized)
            assertEquals(expectedKlib.isFromNativeDistribution, actualKlib.isFromNativeDistribution)
            assertEquals(expectedKlib.targets, actualKlib.targets)
        }
    }

    private fun generateExpectedKlibsFromDistribution(kotlinNativeHome: File): Map<File, KlibInfo> {
        val sourcesDir = kotlinNativeHome.resolve(KONAN_DISTRIBUTION_SOURCES_DIR)
        val basePath = kotlinNativeHome.resolve(KONAN_DISTRIBUTION_KLIB_DIR)

        val result = mutableListOf<KlibInfo>()

        result += KlibInfo(
            path = basePath.resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR, KONAN_STDLIB_NAME),
            sourcePaths = listOf(
                sourcesDir.resolve("kotlin-stdlib-native-sources.zip"),
                sourcesDir.resolve("kotlin-test-anotations-common-sources.zip")
            ),
            isStdlib = true,
            isFromNativeDistribution = true,
            isCommonized = false,
            libraryName = KONAN_STDLIB_NAME,
            targets = null
        )

        result += KlibInfo(
            path = basePath.resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR, "kotlinx-cli"),
            sourcePaths = listOf(
                sourcesDir.resolve("kotlinx-cli-common-sources.zip"),
                sourcesDir.resolve("kotlinx-cli-native-sources.zip")
            ),
            isStdlib = true,
            isCommonized = false,
            isFromNativeDistribution = true,
            libraryName = "kotlinx-cli",
            targets = null
        )

        result += listOf("foo", "bar", "baz").map { name ->
            KlibInfo(
                path = basePath.resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR, "macos_x64", name),
                sourcePaths = emptyList(),
                isFromNativeDistribution = true,
                isCommonized = false,
                libraryName = name,
                isStdlib = false,
                targets = KlibInfo.NativeTargets.NativeTargetsList("macos_x64")
            )
        }

        return result.associateBy { it.path.relativeTo(kotlinNativeHome) }
    }

    private fun generateExpectedCommonizedKlibsFromDistribution(kotlinNativeHome: File): Map<File, KlibInfo> {
        val basePath = kotlinNativeHome.resolve(KONAN_DISTRIBUTION_KLIB_DIR, KONAN_DISTRIBUTION_COMMONIZED_LIBS_DIR)

        fun generateCommonizedKlibsForDir(commonizedLibsDirName: String): Map<File, KlibInfo> {
            val rawTargets = commonizedLibsDirName.split('-').dropLast(1)
            val targets = rawTargets.map {
                when (it) {
                    "ios_x64" -> KonanTarget.IOS_X64
                    "ios_arm64" -> KonanTarget.IOS_ARM64
                    else -> error("Unexpected target: $it")
                }
            }.toSet()

            val result = mutableListOf<KlibInfo>()

            with(basePath.resolve(commonizedLibsDirName)) {
                val libraryDirsToTargets: Map<File, KonanTarget?> =
                    targets.associateBy { resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR, it.name) } +
                            (resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR) to null)

                libraryDirsToTargets.forEach { (libraryDir, _) ->
                    val isCommonized = libraryDir.startsWith(resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR))
                    result += listOf("foo", "bar", "baz").map { name ->
                        KlibInfo(
                            path = libraryDir.resolve(name),
                            sourcePaths = emptyList(),
                            isStdlib = false,
                            isFromNativeDistribution = true,
                            isCommonized = isCommonized,
                            libraryName = name,
                            targets =
                            if (isCommonized) CommonizerIdentity("(${targets.map { it.name }.sorted().joinToString(", ")})") else null
                        )
                    }
                }
            }

            return result.associateBy { it.path.relativeTo(kotlinNativeHome) }
        }

        return generateCommonizedKlibsForDir("ios_arm64-ios_x64-discriminator")
    }

    companion object {
        private val testDataDir: File = IDEA_TEST_DATA_DIR.resolve("configuration/klib")
            .also { assertTrue("Test data directory does not exist: $it", it.isDirectory) }

        private val externalLibsDir = testDataDir.resolve("external-libs")

        private fun File.children(): List<File> = (listFiles()?.toList() ?: emptyList())

        private fun File.resolve(relative: String, next: String, vararg others: String): File {
            var temp = resolve(relative).resolve(next)
            for (other in others)
                temp = temp.resolve(other)

            return temp
        }
    }
}
