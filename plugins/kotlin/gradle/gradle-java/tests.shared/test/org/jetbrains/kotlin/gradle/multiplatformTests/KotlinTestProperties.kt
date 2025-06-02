// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaks
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.SimpleProperties
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

abstract class KotlinTestProperties {
    abstract val kotlinGradlePluginVersion: KotlinToolingVersion

    /**
     * Allows providing test properties. If a pair { key, value } is provided,
     * then all occurrences of string `{{ KEY }}` in test input data will be replaced with `value`.
     *
     * Capitalization and whitespacing doesn't matter in `{{ KEY }}` (so, `{{kEy }}` will also be replaced).
     *
     * NB: providing property here doesn't replace `value` with `{{ KEY }}` in the actual test output data (golden files).
     * However, some checkers do it manually on their own (e.g.
     * [org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker] replaces [kotlinVersion]
     * with `{{ KGP_VERSION }}`.
     */
    abstract fun collectAllProperties(): Map<String, String>

    /**
     * Returns classifiers that will be used for finding relevant expected test-data file for the test.
     * Classifiers allow storing expected test data for "parametrized" tests.
     *
     * Example: suppose the test stores its testdata in the file `expected.ext`.
     * If the [getTestDataClassifiersFromMostSpecific] returns the following sequence:
     *      [ "1.0", "dev", "173"]
     *      [ "1.0", "173" ]
     *      [ "1.0", "dev" ]
     *      [ "1.0" ]
     * then the following sequence of files will be probed:
     *      `expected-1.0-dev-173.ext`
     *      `expected-1.0-173.ext`
     *      `expected-1.0-dev.ext`
     *      `expected-1.0.ext`
     *      `expected.ext` (empty classifiers are always assumed)
     *
     * The files are proved in order; the first found will be used for the current test. Therefore:
     *   - more specific classifier lists should come first, less specific - last.
     *   - order of classifiers matters (this will be exact list of suffixes of the file at the disk that
     *     will be used for test data lookup).
     *
     * Items in sequence don't have to be unique.
     *
     * See [findTestData.kt] for more details
     */
    abstract fun getTestDataClassifiersFromMostSpecific(): Sequence<List<String>>

    fun substituteKotlinTestPropertiesInText(text: String, sourceFile: File): String {
        val allPropertiesValuesById = collectAllProperties()

        var result = text
        allPropertiesValuesById.forEach { (key, value) ->
            result = result.replace(Regex("""\{\s*\{\s*${key}\s*}\s*}""", RegexOption.IGNORE_CASE), value)
        }

        assertNoPatternsLeftUnsubstituted(result, sourceFile, allPropertiesValuesById.keys)
        return result
    }

    private fun assertNoPatternsLeftUnsubstituted(text: String, sourceFile: File, knownProperties: Collection<String>) {
        if (!ANY_TEMPLATE_REGEX.containsMatchIn(text)) return

        // expected testdump files have txt-extension and use `{{ ... }}`-patterns for
        // templating variable parts of outputs (e.g. version of KGP).
        // Those patterns are substituted by specific [ModulePrinterContributor] and thus it's
        // ok to have them unsubstituted here
        if (sourceFile.extension == "txt") return

        error(
            """
                |Not all '{{ ... }}' patterns were substituted in testdata.
                |
                |Available patterns: $knownProperties
                |
                |If you've introduced a new TestProperty, check that it is passed to KotlinTestPropertiesService
                |(simple way to ensure that is to register it in the SimpleProperties)
                | 
                |Full text after substitution:
                |  
                |${text}
            """.trimMargin()
        )
    }

    companion object {
        val ANY_TEMPLATE_REGEX = Regex("""\{\s*\{\s*.*\s*}\s*}""")
    }
}

class KotlinMppTestProperties private constructor(
    private val kotlinGradlePluginVersionFromEnv: KotlinToolingVersion,
    private val gradleVersionFromEnv: GradleVersion,
    private val agpVersionFromEnv: String?,
    private val devModeTweaks: DevModeTweaks?,
) : KotlinTestProperties() {
    override val kotlinGradlePluginVersion: KotlinToolingVersion
        get() = devModeTweaks?.overrideKgpVersion?.let { KotlinToolingVersion(it) } ?: kotlinGradlePluginVersionFromEnv

    val gradleVersion: GradleVersion
        get() = devModeTweaks?.overrideGradleVersion?.let { GradleVersion.version(it) } ?: gradleVersionFromEnv

    val agpVersion: String?
        get() = devModeTweaks?.overrideAgpVersion ?: agpVersionFromEnv

    override fun getTestDataClassifiersFromMostSpecific(): Sequence<List<String>> {
        val kotlinClassifier = with(kotlinGradlePluginVersion) { "$major.$minor.$patch" }
        val gradleClassifier = gradleVersion.version
        val agpClassifier = agpVersion

        return sequenceOf(
            listOfNotNull(kotlinClassifier, gradleClassifier, agpClassifier),
            listOfNotNull(kotlinClassifier, gradleClassifier),
            listOfNotNull(kotlinClassifier, agpClassifier),
            listOfNotNull(gradleClassifier, agpClassifier),
            listOfNotNull(kotlinClassifier),
            listOfNotNull(gradleClassifier),
            listOfNotNull(agpClassifier)
        )
    }

    override fun collectAllProperties(): Map<String, String> {
        val simpleProperties = SimpleProperties(gradleVersion, kotlinGradlePluginVersion)

        // Important! Collect final properties exactly here to get versions with devModeTweaks applied
        return simpleProperties.toMutableMap().apply {
            put(KotlinGradlePluginVersionTestsProperty.id, kotlinGradlePluginVersion.toString())
            put(GradleVersionTestsProperty.id, gradleVersion.version)
            if (agpVersion != null) put(AndroidGradlePluginVersionTestsProperty.id, agpVersion!!)
            if (kotlinGradlePluginVersion < KotlinGradlePluginVersions.V_2_1_0) {
                put("androidTargetPlaceholder", "android()")
                put("iosTargetPlaceholder", "ios()")
            } else {
                put("androidTargetPlaceholder", "androidTarget()")
                put("iosTargetPlaceholder", "iosX64()\niosArm64()\niosSimulatorArm64()")
            }
        }
    }

    companion object {
        fun construct(testConfiguration: TestConfiguration? = null): KotlinMppTestProperties {
            val agpVersion = AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            val gradleVersionRaw = GradleVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersion.version(gradleVersionRaw)

            val kgpVersionRaw = KotlinGradlePluginVersionTestsProperty.resolveFromEnvironment()
            val kgpVersion = KotlinToolingVersion(kgpVersionRaw)

            return KotlinMppTestProperties(
                kgpVersion,
                gradleVersion,
                agpVersion,
                testConfiguration?.getConfiguration(DevModeTestFeature),
            )
        }

        fun constructRaw(kotlinVersion: KotlinToolingVersion, gradleVersion: GradleVersion, agpVersion: String? = null) =
            KotlinMppTestProperties(
                kotlinVersion,
                gradleVersion,
                agpVersion,
                null,
            )
    }
}
