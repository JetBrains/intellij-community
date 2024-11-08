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

class KotlinTestProperties private constructor(
    private val kotlinGradlePluginVersionFromEnv: KotlinToolingVersion,
    private val gradleVersionFromEnv: GradleVersion,
    private val agpVersionFromEnv: String,
    private val devModeTweaks: DevModeTweaks?,
) {
    val kotlinGradlePluginVersion: KotlinToolingVersion
        get() = devModeTweaks?.overrideKgpVersion?.let { KotlinToolingVersion(it) } ?: kotlinGradlePluginVersionFromEnv

    val gradleVersion: GradleVersion
        get() = devModeTweaks?.overrideGradleVersion?.let { GradleVersion.version(it) } ?: gradleVersionFromEnv

    val agpVersion: String
        get() = devModeTweaks?.overrideAgpVersion ?: agpVersionFromEnv



    fun substituteKotlinTestPropertiesInText(text: String, sourceFile: File): String {
        val simpleProperties =  SimpleProperties(gradleVersion, kotlinGradlePluginVersion)

        // Important! Collect final properties exactly here to get versions with devModeTweaks applied
        val allPropertiesValuesById = simpleProperties.toMutableMap().apply {
            put(KotlinGradlePluginVersionTestsProperty.id, kotlinGradlePluginVersion.toString())
            put(GradleVersionTestsProperty.id, gradleVersion.version)
            put(AndroidGradlePluginVersionTestsProperty.id, agpVersion)
            if (kotlinGradlePluginVersion < KotlinGradlePluginVersions.V_2_1_0) {
                put("androidTargetPlaceholder", "android()")
                put("iosTargetPlaceholder", "ios()")
            } else {
                put("androidTargetPlaceholder", "androidTarget()")
                put("iosTargetPlaceholder", "iosX64()\niosArm64()\niosSimulatorArm64()")
            }
        }

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

        fun construct(testConfiguration: TestConfiguration? = null): KotlinTestProperties {
            val agpVersion = AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            val gradleVersionRaw = GradleVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersion.version(gradleVersionRaw)

            val kgpVersionRaw = KotlinGradlePluginVersionTestsProperty.resolveFromEnvironment()
            val kgpVersion = KotlinToolingVersion(kgpVersionRaw)

            return KotlinTestProperties(
                kgpVersion,
                gradleVersion,
                agpVersion,
                testConfiguration?.getConfiguration(DevModeTestFeature),
            )
        }
    }
}
