// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.newTests.resolveFromEnvironment
import org.jetbrains.kotlin.gradle.newTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.SimpleProperties
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

interface KotlinTestPropertiesService {
    fun substituteKotlinTestPropertiesInText(text: String, sourceFile: File): String

    val agpVersion: String
    val gradleVersion: GradleVersion
    val kotlinGradlePluginVersion: KotlinToolingVersion

    companion object {
        fun constructFromEnvironment(
            agpVersionOverride: AndroidGradlePluginVersionTestsProperty.Values? = null,
            gradleVersionOverride: GradleVersionTestsProperty.Values? = null,
            kgpVersionOverride: KotlinGradlePluginVersionTestsProperty.Values? = null,
        ): KotlinTestPropertiesService {
            val agpVersion = agpVersionOverride?.version
                ?: AndroidGradlePluginVersionTestsProperty.resolveFromEnvironment()

            val gradleVersionRaw = gradleVersionOverride?.version
                ?: GradleVersionTestsProperty.resolveFromEnvironment()
            val gradleVersion = GradleVersion.version(gradleVersionRaw)

            val kgpVersionRaw = kgpVersionOverride?.version
                ?: KotlinGradlePluginVersionTestsProperty.resolveFromEnvironment()
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

    override fun substituteKotlinTestPropertiesInText(text: String, sourceFile: File): String {
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
    }
}
