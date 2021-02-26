package org.jetbrains.kotlin

import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.caches.project.DependencyCandidate
import org.jetbrains.kotlin.idea.caches.project.chooseCompatibleDependencies
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget.*
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.junit.Test
import kotlin.test.assertEquals

private val a: SimplePlatform = TestPlatform("a")
private val b: SimplePlatform = TestPlatform("b")
private val c: SimplePlatform = TestPlatform("c")
private val d: SimplePlatform = TestPlatform("d")
private val e: SimplePlatform = TestPlatform("e")
private val f: SimplePlatform = TestPlatform("f")

class LibraryDependenciesCacheTest {

    @Test
    fun `chooseCompatibleDependencies for leaf`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abcCandidate = candidate(platform(a, b, c))
        val bcdCandidate = candidate(platform(b, c, d))
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a), setOf(abcdCandidate, abcCandidate, bcdCandidate, abCandidate, aCandidate, bCandidate)
        )

        assertEquals(
            setOf(abcdCandidate, abcCandidate, abCandidate, aCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is available`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abcCandidate = candidate(platform(a, b, c)) // <- exact match
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(abcdCandidate, abcCandidate, abCandidate, aCandidate, bCandidate)
        )

        assertEquals(
            setOf(abcdCandidate, abcCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is available && no more common candidate is available`() {
        val abcCandidate = candidate(platform(a, b, c)) // <- exact match
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(abcCandidate, abCandidate, aCandidate, bCandidate)
        )

        assertEquals(
            setOf(abcCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is available && noise`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abcCandidate = candidate(platform(a, b, c)) // <- exact match
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))


        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(
                abcdCandidate, abcCandidate, abCandidate, aCandidate, bCandidate,

                /* noise */
                candidate(platform(b, c, d)), candidate(platform(c, d, e)), candidate(platform(d, e)),
                candidate(platform(a, e)), candidate(platform(a, e, f)), candidate(platform(b, e, f)),
                candidate(platform(e)), candidate(platform(f))
            )
        )


        assertEquals(
            setOf(abcdCandidate, abcCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is *not* available`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(abcdCandidate, abCandidate, aCandidate, bCandidate)
        )

        assertEquals(
            setOf(abcdCandidate, abCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies (when exact match is *not* available) is invariant under candidate order`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abCandidate = candidate(platform(a, b))
        val acCandidate = candidate(platform(a, c))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val allCandidates = setOf(abcdCandidate, abCandidate, acCandidate, aCandidate, bCandidate)
        val chosenCandidates = chooseCompatibleDependencies(platform(a, b, c), allCandidates)
        val chosenCandidatesForReverseInput = chooseCompatibleDependencies(platform(a, b, c), allCandidates.reversed().toSet())

        assertEquals(
            chosenCandidates, chosenCandidatesForReverseInput,
            "Expected chosen candidates being invariant under the order of candidates"
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is *not* available && no more common candidate is available`() {
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(abCandidate, aCandidate, bCandidate)
        )

        assertEquals(
            setOf(abCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies when exact match is *not* available (plus noise)`() {
        val abcdCandidate = candidate(platform(a, b, c, d))
        val abCandidate = candidate(platform(a, b))
        val aCandidate = candidate(platform(a))
        val bCandidate = candidate(platform(b))

        val chosen = chooseCompatibleDependencies(
            platform(a, b, c), setOf(
                abcdCandidate, abCandidate, aCandidate, bCandidate,

                /* noise */
                candidate(platform(b, c, d)), candidate(platform(c, d, e)), candidate(platform(d, e)),
                candidate(platform(a, e)), candidate(platform(a, e, f)), candidate(platform(b, e, f)),
                candidate(platform(e)), candidate(platform(f))
            )
        )

        assertEquals(
            setOf(abcdCandidate, abCandidate), chosen
        )
    }

    @Test
    fun `chooseCompatibleDependencies for NativePlatformUnspecifiedTarget`() {
        val commonMain = candidate(platform(NativePlatformUnspecifiedTarget, JdkPlatform(JvmTarget.JVM_1_8)))
        val nativeMain = candidate(platform(LINUX_X64, MACOS_X64, IOS_X64))
        val linuxMain = candidate(platform(LINUX_X64))
        val macosMain = candidate(platform(MACOS_X64))
        val iosMain = candidate(platform(IOS_X64))

        val allCandidates = setOf(commonMain, nativeMain, linuxMain, macosMain, iosMain)

        val chosenForCommonMainExactMatch = chooseCompatibleDependencies(
            platform(NativePlatformUnspecifiedTarget, JdkPlatform(JvmTarget.JVM_1_8)), allCandidates
        )
        assertEquals(setOf(commonMain), chosenForCommonMainExactMatch)


        val chosenForCommonMainInexactMatch = chooseCompatibleDependencies(
            platform(NativePlatformUnspecifiedTarget), allCandidates
        )
        assertEquals(setOf(commonMain, nativeMain), chosenForCommonMainInexactMatch)


        val chosenForNativeMainExactMatch = chooseCompatibleDependencies(
            platform(LINUX_X64, MACOS_X64, IOS_X64), allCandidates
        )
        assertEquals(setOf(commonMain, nativeMain), chosenForNativeMainExactMatch)

        val chosenForNativeMainInexactMatch = chooseCompatibleDependencies(
            platform(LINUX_X64, MACOS_X64, IOS_X64, IOS_ARM32), allCandidates
        )
        assertEquals(setOf(commonMain, nativeMain), chosenForNativeMainInexactMatch)
    }

    @Test
    fun `chooseCompatibleDependencies with NativePlatformUnspecifiedTarget in candidates`() {
        val commonMain = candidate(platform(NativePlatformUnspecifiedTarget))

        assertEquals(
            setOf(commonMain), chooseCompatibleDependencies(platform(NativePlatformUnspecifiedTarget), setOf(commonMain))
        )

        assertEquals(
            setOf(commonMain), chooseCompatibleDependencies(platform(LINUX_X64), setOf(commonMain))
        )

        assertEquals(
            setOf(commonMain), chooseCompatibleDependencies(platform(LINUX_X64, MACOS_X64), setOf(commonMain))
        )
    }
}

private fun candidate(platform: TargetPlatform, uniqueName: String = "arbitrary"): DependencyCandidate =
    DependencyCandidate(containingLibraryId = uniqueName, platform = platform, libraries = emptyList())
