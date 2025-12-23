// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaks
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.KotlinVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.SimpleProperties
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.toKotlinVersion
import java.io.File

class TestVersion<T : Any>(
    val version: T,
    val alias: String?,
) {
    override fun toString(): String {
        return version.toString()
    }

    override fun equals(other: Any?): Boolean {
        val versionType = version.javaClass
        if (versionType.isInstance(other)) {
            return version == other
        }
        return super.equals(other)
    }
}

abstract class KotlinTestProperties {
    /**
     * This is the version of Kotlin that is expected to be used in
     * the synced Workspace Model - dependencies (stdlib and such),
     * languageVersion in facets, etc.
     *
     * For Gradle-sync, it's the version of KGP (for now), see [KotlinMppTestProperties]
     */
    abstract val kotlinVersion: TestVersion<KotlinToolingVersion>

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
        // templating variable parts of outputs (e.g. version of Kotlin).
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

class KotlinMppTestProperties(
    private val kotlinVersionFromEnv: TestVersion<KotlinToolingVersion>,
    private val gradleVersionFromEnv: TestVersion<GradleVersion>,
    private val agpVersionFromEnv: TestVersion<String>?,
    private val devModeTweaks: DevModeTweaks?,
) : KotlinTestProperties() {
    override val kotlinVersion: TestVersion<KotlinToolingVersion>
        get() = devModeTweaks?.overrideKotlinVersion?.let {
            TestVersion(
                KotlinToolingVersion(it),
                null,
            )
        } ?: kotlinVersionFromEnv

    val gradleVersion: TestVersion<GradleVersion>
        get() = devModeTweaks?.overrideGradleVersion?.let {
            TestVersion(
                GradleVersion.version(it),
                null,
            )
        } ?: gradleVersionFromEnv

    val agpVersion: TestVersion<String>?
        get() = devModeTweaks?.overrideAgpVersion?.let {
            TestVersion(
                it,
                null,
            )
        } ?: agpVersionFromEnv


    override fun getTestDataClassifiersFromMostSpecific(): Sequence<List<String>> {
        val kotlinVersionClassifier = with(kotlinVersion.version) { "$major.$minor.$patch" }
        val kotlinAliasClassifier = kotlinVersion.alias
        val gradleClassifier = gradleVersion.version.version
        val agpClassifier = agpVersion?.version
        return sequenceOf(
            *(kotlinAliasClassifier?.let {
                arrayOf(
                    listOfNotNull(kotlinAliasClassifier, gradleClassifier, agpClassifier),
                    listOfNotNull(kotlinAliasClassifier, gradleClassifier),
                    listOfNotNull(kotlinAliasClassifier, agpClassifier),
                    listOfNotNull(kotlinAliasClassifier),
                )
            } ?: emptyArray()),
            listOfNotNull(kotlinVersionClassifier, gradleClassifier, agpClassifier),
            listOfNotNull(kotlinVersionClassifier, gradleClassifier),
            listOfNotNull(kotlinVersionClassifier, agpClassifier),
            listOfNotNull(gradleClassifier, agpClassifier),
            listOfNotNull(kotlinVersionClassifier),
            listOfNotNull(gradleClassifier),
            listOfNotNull(agpClassifier),
        )
    }

    override fun collectAllProperties(): Map<String, String> {
        val simpleProperties =  SimpleProperties(gradleVersion.version, kotlinVersion.version)

        // Important! Collect final properties exactly here to get versions with devModeTweaks applied
        return simpleProperties.toMutableMap().apply {
            put(KotlinVersionTestsProperty.id, kotlinVersion.toString())
            put(GradleVersionTestsProperty.id, gradleVersion.version.version)
            agpVersion?.version?.let {
                put(AndroidGradlePluginVersionTestsProperty.id, it)
            }
            if (kotlinVersion.version < KotlinGradlePluginVersions.V_2_1_0) {
                put("androidTargetPlaceholder", "android()")
                put("iosTargetPlaceholder", """
                    ios()
                    val iosMain by sourceSets.getting
                    """)
            } else {
                put("androidTargetPlaceholder", "androidTarget()")
                put("iosTargetPlaceholder", """
                    iosX64()
                    iosArm64()
                    
                    val iosMain = sourceSets.create("iosMain") {
                      dependsOn(sourceSets.getByName("commonMain"))
                    }
                    val iosTest = sourceSets.create("iosTest") {
                      dependsOn(sourceSets.getByName("commonTest"))
                    }
                    sourceSets.getByName("iosX64Main") { dependsOn(iosMain) }
                    sourceSets.getByName("iosX64Test") { dependsOn(iosTest) }
                    sourceSets.getByName("iosArm64Main") { dependsOn(iosMain) }
                    sourceSets.getByName("iosArm64Test") { dependsOn(iosTest) }
                    
                """.trimIndent())
            }

            if (kotlinVersion.version < KotlinGradlePluginVersions.V_2_2_0) {
                put("minimalSupportedKotlinLanguageVersion", "1.7")
                put("minimalSupportedKotlinVersion", "org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_7")
            } else {
                put("minimalSupportedKotlinLanguageVersion", "1.8")
                put("minimalSupportedKotlinVersion", "org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8")
            }
        }
    }

    companion object {
        fun construct(testConfiguration: TestConfiguration? = null): KotlinMppTestProperties {
            val kotlinVersion = KotlinVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersionTestsProperty.resolveFromEnvironment()
            val agpVersion = AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            return KotlinMppTestProperties(
                TestVersion(
                    KotlinToolingVersion(kotlinVersion.version),
                    kotlinVersion.versionAlias,
                ),
                TestVersion(
                    GradleVersion.version(gradleVersion.version),
                    gradleVersion.versionAlias,
                ),
                TestVersion(
                    agpVersion.version,
                    agpVersion.versionAlias,
                ),
                testConfiguration?.getConfiguration(DevModeTestFeature),
            )
        }

        fun constructRaw(kotlinVersion: TestVersion<KotlinToolingVersion>, gradleVersion: TestVersion<GradleVersion>) =
            KotlinMppTestProperties(
                kotlinVersion,
                gradleVersion,
                null,
                null,
            )
    }
}

/**
 * Just MAJOR.MINOR.PATCH, e.g. "1.9.21" (no `-dev`, no build numbers, etc.)
 */
val KotlinTestProperties.kotlinSimpleVersionString: String get() = kotlinVersion.version.toKotlinVersion().toString()
