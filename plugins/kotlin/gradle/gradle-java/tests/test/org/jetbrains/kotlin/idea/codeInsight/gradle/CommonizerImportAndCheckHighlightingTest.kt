// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.DependencyScope.COMPILE
import com.intellij.openapi.roots.DependencyScope.PROVIDED
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.Assume
import org.junit.Test
import java.io.PrintStream

class CommonizerImportAndCheckHighlightingTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "commonizerImportAndCheckHighlighting"

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)

    override fun setUp() {
        val testedVersions = setOf(KotlinGradlePluginVersions.lastStable, KotlinGradlePluginVersions.latest)
        Assume.assumeTrue(
            "CommonizerImportAndCheckHighlightingTest only runs against $testedVersions",
            kotlinPluginVersion in testedVersions,
        )
        super.setUp()
    }

    @Test
    fun testWithPosix() {
        configureByFiles()
        importProject(false)
        val highlightingCheck = createHighlightingCheck()

        checkProjectStructure(false, false, false) {
            val scope = if (isKgpDependencyResolutionEnabled()) COMPILE else PROVIDED

            module("project.p1.nativeMain") {
                highlightingCheck(module)
                libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
            }

            module("project.p1.appleAndLinuxMain") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
                }
            }

            module("project.p1.linuxMain") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
                }
            }

            module("project.p1.appleMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
                }
            }

            module("project.p1.iosMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
                }
            }

            module("project.p1.linuxArm64Main") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                        libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), scope)
                    }
                    libraryDependencyByUrl(Regex(""".*/linux_arm64/.*posix.*"""), scope)
                }
            }

            module("project.p1.linuxX64Main") {
                if (SystemInfo.isMac || SystemInfo.isLinux) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                        libraryDependencyByUrl(Regex(""".*/\(linux_arm64, linux_x64\)/.*posix.*"""), scope)
                    }
                    libraryDependencyByUrl(Regex(""".*/linux_x64/.*posix.*"""), scope)
                }
            }

            module("project.p1.macosMain") {
                if (SystemInfo.isMac) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    if (kotlinPluginVersion < KotlinToolingVersion("1.5.30-dev")) {
                        libraryDependencyByUrl(Regex(""".*/\(.*macos_x64.*\)/.*posix.*"""), scope)
                    }
                    libraryDependencyByUrl(Regex(""".*/macos_x64/.*posix.*"""), scope)
                }
            }

            module("project.p1.windowsMain") {
                if (SystemInfo.isWindows) {
                    highlightingCheck(module)
                    libraryDependencyByUrl(Regex(""".*withPosix.*"""), scope)
                    libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
                }
            }
        }
    }

    @Test
    fun testSingleNativeTarget() {
        configureByFiles()
        importProject()
        val highlightingCheck = createHighlightingCheck()

        checkProjectStructure(false, false, false) {
            val scope = if (isKgpDependencyResolutionEnabled()) COMPILE else PROVIDED

            module("project.p1.nativeMain") {
                highlightingCheck(module)
                libraryDependency(Regex("""Kotlin/Native.*posix.*"""), scope)
            }

            module("project.p1.commonMain") {
                highlightingCheck(module)
                libraryDependency(Regex("""Kotlin/Native.*posix.*"""), scope)
            }
        }
    }


    @Test
    fun testSingleSupportedNativeTargetDependencyPropagation() {
        configureByFiles()
        importProject()
        val highlightingCheck = createHighlightingCheck(severityLevel = HighlightSeverity.ERROR)

        checkProjectStructure(false, false, false) {
            module("project.p1.commonMain") {
                highlightingCheck(module)
            }

            val scope = if (isKgpDependencyResolutionEnabled()) COMPILE else PROVIDED

            module("project.p1.nativeMainParent") {
                highlightingCheck(module)
                libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
            }

            module("project.p1.nativeMain") {
                highlightingCheck(module)
                libraryDependencyByUrl(Regex(""".*posix.*"""), scope)
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


    @Test
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
                    val posixLibraryNameRegex = nativeDistLibraryDependency("posix", libraryPlatform = null)

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
                    val platforms = if (isKgpDependencyResolutionEnabled() && !HostManager.hostIsMac) // KTIJ-24573
                        "linux_arm64, linux_x64, mingw_x64, mingw_x86"
                    else "linux_arm64, linux_x64, macos_x64, mingw_x64, mingw_x86"
                    val withPosixLibraryNameRegex = Regex(
                        """Gradle: project:p1-cinterop-withPosix.*\($platforms\).*"""
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

                    if (!isKgpDependencyResolutionEnabled()) { // FIXME: KTIJ-24560
                        assertEquals(
                            "Expected 'withPosix' (c-interop) to be 'module level'",
                            LibraryLevel.MODULE.name.toLowerCase(),
                            withPosix.libraryLevel.toLowerCase()
                        )
                    }
                }
            }

            module("project.p1.linuxX64Main") {

                /**
                 * Assert that a library coming from the native distribution has the correct libraryName
                 * and is considered 'project level' for a leaf source set
                 */
                run {
                    val posixLibraryNameRegex = nativeDistLibraryDependency("posix", null)

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
                        """Gradle: project:p1-cinterop-withPosix( \| |:)linux_x64"""
                    )

                    val withPosixEntriesMatchingNamingScheme = module.rootManager.orderEntries
                        .filterIsInstance<LibraryOrderEntry>()
                        .filter { it.libraryName.orEmpty().matches(withPosixLibraryNameRegex) }

                    assertEquals(
                        "Expected exactly one library matching 'withPosixLibraryNameRegex'",
                        1, withPosixEntriesMatchingNamingScheme.size
                    )

                    val withPosix = withPosixEntriesMatchingNamingScheme.single()

                    if (!isKgpDependencyResolutionEnabled()) { // FIXME: KTIJ-24560
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
