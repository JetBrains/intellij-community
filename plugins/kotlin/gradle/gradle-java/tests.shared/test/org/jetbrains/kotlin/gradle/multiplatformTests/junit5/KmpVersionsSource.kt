// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.KmpVersionPreset.BOOTSTRAP
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.KmpVersionPreset.MIN
import org.jetbrains.kotlin.gradle.multiplatformTests.junit5.KmpVersionPreset.STABLE
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.KotlinVersionTestsProperty
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * JUnit-5 argument source emitting [KmpVersions] triples for `@ParameterizedClass`-annotated KMP
 * test classes. Replaces the JUnit-4 era trio of environment variables
 * (`KGP_VERSION`, `GRADLE_VERSION`, `AGP_VERSION`) with a single class-level annotation.
 *
 * Selection order (first match wins):
 * 1. `-Dkmp.versions.to.run=…` system property — comma-separated list of preset names. Useful for
 *    explicit local debugging.
 * 2. On TeamCity (env `TEAMCITY_VERSION` set), the bucket is identified by the trio
 *    `KGP_VERSION` / `GRADLE_VERSION` / `AGP_VERSION` env vars. We return the single
 *    [KmpVersionPreset] whose aliases match the bucket; if no preset matches (e.g. a `KGP=LATEST`
 *    bucket pinned to non-canonical Gradle/AGP), the stream is empty and the test class is skipped
 *    via `@ParameterizedClass(allowZeroInvocations = true)`.
 * 3. Otherwise (local runs) — emit [KmpVersionsArgumentsProvider.DEFAULT_PRESETS].
 */
@Retention(AnnotationRetention.RUNTIME)
@ArgumentsSource(KmpVersionsArgumentsProvider::class)
annotation class KmpVersionsSource

class KmpVersionsArgumentsProvider : ArgumentsProvider {
    companion object {
        val DEFAULT_PRESETS: List<KmpVersionPreset> = listOf(BOOTSTRAP, STABLE, MIN)
        const val PROPERTY_NAME: String = "kmp.versions.to.run"
    }

    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
        return resolvePresets().asSequence().map { Arguments.of(it.resolve()) }.asStream()
    }

    private fun resolvePresets(): List<KmpVersionPreset> {
        val raw = System.getProperty(PROPERTY_NAME)
        if (!raw.isNullOrBlank()) return parsePresetsFromProperty(raw)
        if (System.getenv("TEAMCITY_VERSION") != null) return resolvePresetForTeamCityBucket()
        return DEFAULT_PRESETS
    }

    private fun parsePresetsFromProperty(raw: String): List<KmpVersionPreset> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { name ->
                runCatching { KmpVersionPreset.valueOf(name.uppercase()) }
                    .getOrElse { error("Unknown KMP version preset: '$name'. Allowed: ${KmpVersionPreset.entries.joinToString()}.") }
            }

    /**
     * Picks the unique preset matching this TC bucket's `KGP/Gradle/AGP` aliases.
     * Returns an empty list when the bucket's triple does not match any canonical preset
     * (e.g. KGP=LATEST in a Gradle=STABLE / AGP=STABLE bucket): such buckets emit no invocations
     * so each preset runs in exactly one bucket.
     */
    private fun resolvePresetForTeamCityBucket(): List<KmpVersionPreset> {
        val env = System.getenv()
        val kgpAlias = env[KotlinVersionTestsProperty.id.uppercase()] ?: return DEFAULT_PRESETS
        val gradleAlias = env[GradleVersionTestsProperty.id.uppercase()] ?: return DEFAULT_PRESETS
        val agpAlias = env[AndroidGradlePluginVersionTestsProperty.id.uppercase()] ?: return DEFAULT_PRESETS

        return KmpVersionPreset.entries.filter { preset ->
            preset.kotlin.versionAlias == kgpAlias &&
                    preset.gradle.versionAlias == gradleAlias &&
                    preset.agp.versionAlias == agpAlias
        }
    }
}
