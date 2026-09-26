// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.io.delete
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk

/**
 * Tool for updating test data in Kotlin Multiplatform tests.
 *
 * KMP tests are organized into 3 buckets by KGP (Kotlin Gradle Plugin) version:
 * - Minimum supported version
 * - STABLE (current stable release)
 * - LATEST (Bootstrap)
 *
 * When a new Kotlin version is released, test data is promoted: LATEST becomes STABLE with fixes.
 * This tool automates the promotion by finding all `-STABLE` test fixture files and
 * replacing them with their `-LATEST` counterparts.
 *
 * Run manually after KGP version updates to sync stable snapshots with latest outputs.
 */
object TestFixtureUpdater {
    @JvmStatic
    fun main(args: Array<String>) {
        val communityPath = Path(PlatformTestUtil.getCommunityPath()).takeIf { it.exists() } ?: error("Cannot find community path")
        val testFixturePath = communityPath.resolve("plugins/kotlin/idea/tests/testData/gradle/multiplatform/core")
            .takeIf { it.exists() } ?: error("Cannot find test fixture path")
        testFixturePath.walk().filter { it.nameWithoutExtension.endsWith("-STABLE") }.forEach { stableFixture ->
            val latestVersionPath = stableFixture.resolveSibling(stableFixture.name.replace("-STABLE", "-LATEST")).takeIf { it.exists() }
                ?: stableFixture.resolveSibling(stableFixture.name.replace("-STABLE", "")).takeIf { it.exists() }
                ?: run { println("Cannot find latest version for ${stableFixture.absolute()}"); return@forEach }
            stableFixture.delete()
            latestVersionPath.copyTo(stableFixture)
        }
    }
}