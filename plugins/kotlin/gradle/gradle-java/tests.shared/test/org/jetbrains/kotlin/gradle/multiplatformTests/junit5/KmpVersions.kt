// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.TestVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.KotlinVersionTestsProperty
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

/**
 * Triple of Kotlin / Gradle / AGP versions injected via JUnit-5 `@ParameterizedClass`.
 * Replaces the old env-var-driven `KotlinMppTestProperties.construct()` that relied on three separate environment
 * variables (`KGP_VERSION`, `GRADLE_VERSION`, `AGP_VERSION`).
 */
data class KmpVersions(
    val kotlin: TestVersion<KotlinToolingVersion>,
    val gradle: TestVersion<GradleVersion>,
    val agp: TestVersion<String>,
) {
    /**
     * Display name shown by JUnit5 in the run gutter and reports.
     */
    override fun toString(): String {
        return "[${kotlin.version}, ${gradle.version}, ${agp.version}]"
    }
}

/**
 * Curated combinations of Kotlin / Gradle / AGP versions exercised by KMP importing tests.
 * Maps directly onto the existing per-property `Value` enums so the resolved versions stay in sync
 * with the rest of the test infrastructure (`{{kotlinVersion}}` / `{{gradleVersion}}` substitution,
 * `@PluginTargetVersions` filtering, KGP-version-dependent test-data lookup).
 *
 * Locally `@KmpVersionsSource` emits every preset by default. On CI each bucket sets
 * `-Dkmp.versions.to.run=STABLE,MIN` etc. to restrict the run to specific presets — replacing the
 * previous `KGP_VERSION`/`GRADLE_VERSION`/`AGP_VERSION` env triplet.
 */
enum class KmpVersionPreset(
    internal val kotlin: KotlinVersionTestsProperty.Value,
    internal val gradle: GradleVersionTestsProperty.Value,
    internal val agp: AndroidGradlePluginVersionTestsProperty.Value,
) {
    MIN(
        kotlin = KotlinVersionTestsProperty.Value.MinSupported,
        gradle = GradleVersionTestsProperty.Value.ForMinAgp,
        agp = AndroidGradlePluginVersionTestsProperty.Value.MinSupported,
    ),
    STABLE(
        kotlin = KotlinVersionTestsProperty.Value.LatestStable,
        gradle = GradleVersionTestsProperty.Value.ForStableAgp,
        agp = AndroidGradlePluginVersionTestsProperty.Value.LatestStable,
    ),
    BOOTSTRAP(
        kotlin = KotlinVersionTestsProperty.Value.Latest,
        gradle = GradleVersionTestsProperty.Value.ForAlphaAgp,
        agp = AndroidGradlePluginVersionTestsProperty.Value.Alpha,
    );

    fun resolve(): KmpVersions = KmpVersions(
        kotlin = TestVersion(KotlinToolingVersion(kotlin.version), kotlin.versionAlias),
        gradle = TestVersion(GradleVersion.version(gradle.version), gradle.versionAlias),
        agp = TestVersion(agp.version, agp.versionAlias)
    )
}
