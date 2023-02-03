// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.infra

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.newTests.resolveFromEnvironment
import org.jetbrains.kotlin.gradle.newTests.testFeatures.DevModeTweaksImpl
import org.jetbrains.kotlin.gradle.newTests.infra.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.infra.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.infra.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.infra.testProperties.SimpleProperties
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

class KotlinTestProperties private constructor(
    val kotlinGradlePluginVersion: KotlinToolingVersion,
    val gradleVersion: GradleVersion,
    val agpVersion: String,
    propertiesValuesById: Map<String, String>,
) {
    private val allPropertiesValuesById = propertiesValuesById.toMutableMap().apply {
        put(KotlinGradlePluginVersionTestsProperty.id, kotlinGradlePluginVersion.toString())
        put(GradleVersionTestsProperty.id, gradleVersion.version)
        put(AndroidGradlePluginVersionTestsProperty.id, agpVersion)
    }

    fun substituteKotlinTestPropertiesInText(text: String, sourceFile: File): String {
        var result = text
        allPropertiesValuesById.forEach { (key, value) ->
            result = result.replace(Regex("""\{\s*\{\s*${key}\s*\}\s*\}""", RegexOption.IGNORE_CASE), value)
        }

        assertNoPatternsLeftUnsubstituted(result, sourceFile)
        return result
    }

    private fun assertNoPatternsLeftUnsubstituted(text: String, sourceFile: File) {
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
                |Available patterns: ${allPropertiesValuesById.keys}
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
        val ANY_TEMPLATE_REGEX = Regex("""\{\s*\{\s*.*\s*\}\s*\}""")

        fun constructFromEnvironment(): KotlinTestProperties {
            val devModeTweaks = DevModeTweaksImpl()

            val agpVersion = devModeTweaks.overrideAgpVersion?.version
                ?: AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            val gradleVersionRaw = devModeTweaks.overrideGradleVersion?.version
                ?: GradleVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersion.version(gradleVersionRaw)

            val kgpVersionRaw = devModeTweaks.overrideKgpVersion?.version
                ?: KotlinGradlePluginVersionTestsProperty.resolveFromEnvironment()
            val kgpVersion = KotlinToolingVersion(kgpVersionRaw)

            val simpleProperties = SimpleProperties(gradleVersion, kgpVersion)

            return KotlinTestProperties(kgpVersion, gradleVersion, agpVersion, simpleProperties)
        }
    }
}
