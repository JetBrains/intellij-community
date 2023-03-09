// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

interface KotlinTestPropertiesService {
    val defaultProperties: Map<String, String>
    fun substituteKotlinTestPropertiesInText(text: String, properties: Map<String, String>? = null): String

    val gradleVersion: String
    val kotlinGradlePluginVersion: KotlinToolingVersion
}

class KotlinTestPropertiesServiceImpl : KotlinTestPropertiesService {
    // TODO fix versions when version matrix is ready
    override val gradleVersion: String = "7.5.1"
    override val kotlinGradlePluginVersion: KotlinToolingVersion = KotlinGradlePluginVersions.latest

    override val defaultProperties: Map<String, String> =
        mapOf(
            "kotlin_plugin_version" to kotlinGradlePluginVersion.toString(),

            "kts_kotlin_plugin_repositories" to GradleKotlinTestUtils.listRepositories(
                useKts = true, GradleVersion.version(gradleVersion), kotlinGradlePluginVersion
            ),

            "kotlin_plugin_repositories" to GradleKotlinTestUtils.listRepositories(
                useKts = false, GradleVersion.version(gradleVersion), kotlinGradlePluginVersion
            ),

            "android_gradle_plugin_version" to "7.4.0-beta03",
            "compile_sdk_version" to "31",
            "build_tools_version" to "28.0.3",

            // TODO fix hmpp flags when versions matrix is ready
            "enable_hmpp_flags" to "",
            "disable_hmpp_flags" to "kotlin.mpp.hierarchicalStructureSupport=false"
        )

    override fun substituteKotlinTestPropertiesInText(text: String, properties: Map<String, String>?): String {
        var result = text
        (properties ?: defaultProperties).forEach { (key, value) ->
            result = result.replace(Regex("""\{\s*\{\s*${key}\s*}\s*}"""), value)
        }

        return result
    }
}
