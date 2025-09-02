// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scripting.gradle.roots

import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator.NotificationKind.dontCare
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator.NotificationKind.wasNotImportedAfterCreation
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class GradleBuildRootsLocatorTest : AbstractGradleBuildRootsLocatorTest() {
    fun testNewBuildGradleKtsNearProjectRoot() {
        // the build.gradle.kts under the project root will be definitive import at next import
        // so, we should not treat it as unlinked
        newImportedGradleProject("imported", relativeScripts = listOf())

        assertNotificationKind("imported/build.gradle.kts", wasNotImportedAfterCreation)
    }

    fun testBuildGradleKtsNearProjectRoot() {
        newImportedGradleProject("imported", relativeScripts = listOf("build.gradle.kts"))

        assertNotificationKind("imported/build.gradle.kts", dontCare)
    }
}
