// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomGradlePropertiesDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsLinkingDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.AllFilesUnderContentRootConfigurationDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.DocumentationCheckerDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingCheckDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources.LibrarySourcesCheckDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.WorkspaceChecksDsl

/**
 * Receiver for `kotlinTestFeaturesFixture { ... }` and `features.check(project, root) { ... }` blocks.
 *
 * Inherits [TestConfiguration] so the `writeAccess` cast inside the legacy `*Dsl` interfaces keeps working,
 * and mixes in every public DSL marker so extension properties like `hideResourceRoots` / `onlyCheckers` are
 * resolved without forcing the test class to implement the DSL interfaces itself.
 */
open class KotlinTestFeaturesScope : TestConfiguration(),
    WorkspaceChecksDsl,
    GradleProjectsPublishingDsl,
    GradleProjectsLinkingDsl,
    HighlightingCheckDsl,
    DevModeTweaksDsl,
    AllFilesUnderContentRootConfigurationDsl,
    RunConfigurationChecksDsl,
    CustomGradlePropertiesDsl,
    DocumentationCheckerDsl,
    KotlinMppTestHooksDsl,
    LibrarySourcesCheckDsl {

    /**
     * Typed entry point for tweaking a single feature configuration without manual `writeAccess.getConfiguration(...)`.
     */
    fun <V : Any> configure(feature: TestFeature<V>, block: V.() -> Unit) {
        getConfiguration(feature).block()
    }
}
