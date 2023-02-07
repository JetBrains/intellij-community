// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.io.File

internal fun PrinterContext.foldKonanDist(orderEntries: List<String>, module: Module): List<String> {
    val platform = module.platform
    if (!platform.isNative()) return orderEntries

    val expectedContent = expectedKonanDistForHostAndTarget(platform).toMutableSet()
    val result = mutableListOf<String>()
    val actualKonanDist = mutableListOf<String>()
    for (entry in orderEntries) {
        val match = expectedContent.find { entry.startsWith(it) }
        if (match != null) {
            expectedContent.remove(match)
            actualKonanDist += entry
        } else {
            result += entry
        }
    }

    // Some of the expected content were unmatched, bail out
    if (expectedContent.isNotEmpty()) return orderEntries

    val stubEntry = NATIVE_DISTRIBUTION_STUB_ENTRY

    return result + stubEntry
}

/**
 * We're doing quite a trick here with filtering enabled targets. The general intuition is that it behaves
 * similarly to our toolchain, but there are interesting behaviour for tests convenience.
 *
 * Note that if all the targets are unsupported (e.g. it's just iosArm64 target, and we're on linux),
 * then this method returns *emptySet*. This is very important, because if the returned value
 * is a proper subset of actual order entries list, then [foldKonanDist] replaces those entries
 * with [NATIVE_DISTRIBUTION_STUB_ENTRY]. But emptySet is a proper subset of any set! So,
 * in such cases, no assertions is run effectively (because subset-check is trivially true), and
 * [NATIVE_DISTRIBUTION_STUB_ENTRY] is always added to the order-entry list.
 *
 * This is a desired and explicitly designed oddity. It allows not only to make tests using Apple-targets
 * green on non-mac hosts, but also generate properly-looking testdata for Apple-targets on non-Mac hosts,
 * which will also make test pass (and run proper assertions!) when it will be run on actual Mac host
 */
private fun PrinterContext.expectedKonanDistForHostAndTarget(target: TargetPlatform): Set<String> {
    require(target.isNative()) { "Can't get expected Kotlin/Native Distribution content for non-Native platform $target" }

    val konanTargets = target.componentPlatforms
        .map { (it as NativePlatformWithTarget).target }
        .filter { it in ENABLED_TARGETS }

    val families = konanTargets.map { it.family }.distinct()
    val distributionsToCommonize = families.map { expectedKonanDistForFamily(it) }

    return if (distributionsToCommonize.isEmpty())
        emptySet()
    else
        distributionsToCommonize.reduce { result, next -> result intersect next }
}

private fun PrinterContext.expectedKonanDistForFamily(family: Family): Set<String> {
    val versionClassifier = kotlinGradlePluginVersion.toKotlinVersion().toString()

    val moreSpecificDist = File(PATH_TO_EXPECTED_KONAN_DIST_CONTENTS, family.name.toLowerCaseAsciiOnly() + "-$versionClassifier.txt")
        .takeIf { it.exists() }

    val defaultDist = File(PATH_TO_EXPECTED_KONAN_DIST_CONTENTS, family.name.toLowerCaseAsciiOnly() + ".txt")

    val chosenDist = moreSpecificDist ?: defaultDist
    check(chosenDist.exists()) {
        "Can't find file with serialized expected Kotlin/Native Distribution content for $family, " +
                "looked at: ${chosenDist.canonicalPath}"
    }

    return chosenDist.readLines().toSet()
}

private const val NATIVE_DISTRIBUTION_STUB_ENTRY = "Kotlin/Native {{KGP_VERSION}} - DISTRIBUTION STUB"
private val PATH_TO_EXPECTED_KONAN_DIST_CONTENTS = File(IDEA_TEST_DATA_DIR, "gradle/newMppTests/expectedKonanDistContents")
private val ENABLED_TARGETS = HostManager().enabled.toSet()
