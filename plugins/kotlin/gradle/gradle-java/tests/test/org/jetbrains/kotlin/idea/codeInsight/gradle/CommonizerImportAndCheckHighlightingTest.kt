// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.DependencyScope.PROVIDED
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.PrintStream

abstract class CommonizerImportAndCheckHighlightingTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "commonizerImportAndCheckHighlighting"

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)

    class TestBucket1 : CommonizerImportAndCheckHighlightingTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.20+")
        fun testWithPosix() {
            configureByFiles()
            importProject(false)
            val highlightingCheck = createHighlightingCheck()

            checkProjectStructure(false, false, false) {
                module("project.p1.nativeMain") {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                }

                module("project.p1.appleAndLinuxMain") {
                    if (SystemInfo.isMac || SystemInfo.isLinux) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.linuxMain") {
                    if (SystemInfo.isMac || SystemInfo.isLinux) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.appleMain") {
                    if (SystemInfo.isMac) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.iosMain") {
                    if (SystemInfo.isMac) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.linuxArm64Main") {
                    if (SystemInfo.isMac || SystemInfo.isLinux) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                            libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), PROVIDED)
                        }
                        libraryDependencyByUrl(Regex(""".*/linux_arm64/.*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.linuxX64Main") {
                    if (SystemInfo.isMac || SystemInfo.isLinux) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                            libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), PROVIDED)
                        }
                        libraryDependencyByUrl(Regex(""".*/linux_x64/.*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.macosMain") {
                    if (SystemInfo.isMac) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                            libraryDependencyByUrl(Regex(""".*/\(.*macos_x64.*\)/.*posix.*"""), PROVIDED)
                        }
                        libraryDependencyByUrl(Regex(""".*/macos_x64/.*posix.*"""), PROVIDED)
                    }
                }

                module("project.p1.windowsMain") {
                    if (SystemInfo.isWindows) {
                        highlightingCheck(module)
                        libraryDependencyByUrl(Regex(""".*withPosix.*"""), PROVIDED)
                        libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                    }
                }
            }
        }
    }

    class TestBucket2 : CommonizerImportAndCheckHighlightingTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.20+")
        fun testSingleNativeTarget() {
            configureByFiles()
            importProject()
            val highlightingCheck = createHighlightingCheck()

            checkProjectStructure(false, false, false) {
                module("project.p1.nativeMain") {
                    highlightingCheck(module)
                    libraryDependency(Regex("""Kotlin/Native.*posix.*"""), PROVIDED)
                }

                module("project.p1.commonMain") {
                    highlightingCheck(module)
                    libraryDependency(Regex("""Kotlin/Native.*posix.*"""), PROVIDED)
                }
            }
        }
    }

    class TestBucket3 : CommonizerImportAndCheckHighlightingTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.20+")
        fun testSingleSupportedNativeTargetDependencyPropagation() {
            configureByFiles()
            importProject()
            val highlightingCheck = createHighlightingCheck(severityLevel = HighlightSeverity.ERROR)

            checkProjectStructure(false, false, false) {
                module("project.p1.commonMain") {
                    highlightingCheck(module)
                }

                module("project.p1.nativeMainParent") {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                }

                module("project.p1.nativeMain") {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), PROVIDED)
                }

                module("project.p1.nativePlatformMain") {
                    highlightingCheck(module)

                    val posixDependencies = module.rootManager.orderEntries.filterIsInstance<LibraryOrderEntry>().filter { entry ->
                        entry.library?.getUrls(OrderRootType.CLASSES)?.any { it.matches(Regex(""".*posix.*""")) } ?: false
                    }

                    if (posixDependencies.isEmpty()) {
                        report("Missing dependency on posix")
                    }

                    if (posixDependencies.size > 2) {
                        // Gradle plugin detail whether it decides to use commonized expect/actual, or just the original klib
                        report("Expected one or two dependencies on posix")
                    }
                }
            }
        }

    }

    class TestBucket4 : CommonizerImportAndCheckHighlightingTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.6.20+")
        fun testLibraryNamesAndLevel() {
            configureByFiles()
            importProject(true)

            checkProjectStructure(false, false, false) {
                module("project.p1.nativeMain") {

                    /**
                     * Assert that a commonized Library, coming from the native distribution
                     * has a proper libraryName and is considered 'project level'
                     */
                    run {
                        val posixLibraryNameRegex = Regex(
                            """Kotlin/Native $kotlinPluginVersion - posix \|.*"""
                        )

                        val posixEntriesMatchingNamingScheme = module.rootManager.orderEntries
                            .filterIsInstance<LibraryOrderEntry>()
                            .filter { it.libraryName.orEmpty().matches(posixLibraryNameRegex) }

                        assertEquals(
                            "Expected exactly one library matching the posix naming schema",
                            1, posixEntriesMatchingNamingScheme.size
                        )

                        val posix = posixEntriesMatchingNamingScheme.single()

                        assertEquals(
                            "Expected posix being 'PROJECT' level",
                            LibraryLevel.PROJECT.name.toLowerCase(),
                            posix.libraryLevel.toLowerCase(),
                        )
                    }

                    /**
                     * Assert that a commonized library, coming from a project's c-interop
                     * has a proper libraryName and is *not* considered 'project level'
                     */
                    run {
                        val withPosixLibraryNameRegex = Regex(
                            if (kotlinPluginVersion > KotlinToolingVersion("1.5.30-dev"))
                                """Gradle: project:p1-cinterop-withPosix \| \[\(linux_arm64, linux_x64, macos_x64, mingw_x64, mingw_x86\)]"""
                            else """Gradle: project:p1-cinterop-withPosix \| \[\(\(linux_arm64, linux_x64\), \(mingw_x64, mingw_x86\), macos_x64\)]"""
                        )

                        val libraryOrderEntries = module.rootManager.orderEntries
                            .filterIsInstance<LibraryOrderEntry>()

                        val withPosixEntriesMatchingNamingScheme = libraryOrderEntries
                            .filter { it.libraryName.orEmpty().matches(withPosixLibraryNameRegex) }

                        assertEquals(
                            "Expected exactly one library matching 'withPosix' naming schema\n" +
                                    "Libraries: ${libraryOrderEntries.map { it.libraryName }}",
                            1, withPosixEntriesMatchingNamingScheme.size
                        )

                        val withPosix = withPosixEntriesMatchingNamingScheme.single()

                        assertEquals(
                            "Expected 'withPosix' (c-interop) to be 'module level'",
                            LibraryLevel.MODULE.name.toLowerCase(),
                            withPosix.libraryLevel.toLowerCase()
                        )
                    }
                }

                module("project.p1.linuxX64Main") {

                    /**
                     * Assert that a library coming from the native distribution has the correct libraryName
                     * and is considered 'project level' for a leaf source set
                     */
                    run {
                        val posixLibraryNameRegex = Regex(
                            """Kotlin/Native $kotlinPluginVersion - posix \|.*"""
                        )

                        val posixEntriesMatchingNamingScheme = module.rootManager.orderEntries
                            .filterIsInstance<LibraryOrderEntry>()
                            .filter { it.libraryName.orEmpty().matches(posixLibraryNameRegex) }

                        assertTrue(
                            "Expected one or two posix order entries for posix on a leaf source set",
                            posixEntriesMatchingNamingScheme.isNotEmpty() && posixEntriesMatchingNamingScheme.size <= 2
                        )

                        posixEntriesMatchingNamingScheme.forEach { posix ->
                            assertEquals(
                                "Expected posix being 'PROJECT' level",
                                LibraryLevel.PROJECT.name.toLowerCase(),
                                posix.libraryLevel.toLowerCase(),
                            )
                        }
                    }

                    /**
                     * Assert that a c-interop library has the correct libraryName and scope on a leaf source set.
                     */
                    run {
                        val withPosixLibraryNameRegex = Regex(
                            """Gradle: project:p1-cinterop-withPosix \| linux_x64"""
                        )

                        val withPosixEntriesMatchingNamingScheme = module.rootManager.orderEntries
                            .filterIsInstance<LibraryOrderEntry>()
                            .filter { it.libraryName.orEmpty().matches(withPosixLibraryNameRegex) }

                        assertEquals(
                            "Expected exactly one library matching 'withPosixLibraryNameRegex'",
                            1, withPosixEntriesMatchingNamingScheme.size
                        )

                        val withPosix = withPosixEntriesMatchingNamingScheme.single()

                        assertEquals(
                            "Expected 'withPosix' (c-interop) to be 'module level'",
                            LibraryLevel.MODULE.name.toLowerCase(),
                            withPosix.libraryLevel.toLowerCase()
                        )
                    }
                }
            }
        }
    }
}
