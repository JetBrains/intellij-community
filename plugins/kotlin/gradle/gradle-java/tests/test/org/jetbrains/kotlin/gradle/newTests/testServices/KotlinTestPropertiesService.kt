// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.newTests.resolveFromEnvironment
import org.jetbrains.kotlin.gradle.newTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.SimpleProperties
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

interface KotlinTestPropertiesService {
    fun substituteKotlinTestPropertiesInText(text: String): String

    val agpVersion: String
    val gradleVersion: GradleVersion
    val kotlinGradlePluginVersion: KotlinToolingVersion

    companion object {
        fun constructFromEnvironment(): KotlinTestPropertiesService {
            val agpVersion = AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            val gradleVersionRaw = GradleVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersion.version(gradleVersionRaw)

            val kgpVersionRaw = KotlinGradlePluginVersionTestsProperty.resolveFromEnvironment()
            val kgpVersion = KotlinToolingVersion(kgpVersionRaw)

            val simpleProperties = SimpleProperties(gradleVersion, kgpVersion)

            return KotlinTestPropertiesServiceImpl(kgpVersion, gradleVersion, agpVersion, simpleProperties)
        }
    }
}

class KotlinTestPropertiesServiceImpl(
    override val kotlinGradlePluginVersion: KotlinToolingVersion,
    override val gradleVersion: GradleVersion,
    override val agpVersion: String,
    propertiesValuesById: Map<String, String>,
) : KotlinTestPropertiesService {
    private val allPropertiesValuesById = propertiesValuesById.toMutableMap().apply {
        put(KotlinGradlePluginVersionTestsProperty.id, kotlinGradlePluginVersion.toString())
        put(GradleVersionTestsProperty.id, gradleVersion.version)
        put(AndroidGradlePluginVersionTestsProperty.id, agpVersion)
    }

    val defaultProperties: Map<String, String> =
        mapOf(
            // TODO fix hmpp flags when versions matrix is ready
            "enable_hmpp_flags" to "",
            "disable_hmpp_flags" to "kotlin.mpp.hierarchicalStructureSupport=false"
        )

    override fun substituteKotlinTestPropertiesInText(text: String): String {
        var result = text
        allPropertiesValuesById.forEach { (key, value) ->
            result = result.replace(Regex("""\{\s*\{\s*${key}\s*}\s*}"""), value)
        }

        return result
    }
}
