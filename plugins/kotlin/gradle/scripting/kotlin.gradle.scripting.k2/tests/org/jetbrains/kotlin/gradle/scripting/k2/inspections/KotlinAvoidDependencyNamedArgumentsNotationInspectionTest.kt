// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDependencyNamedArgumentsNotationInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidDependencyNamedArgumentsNotationInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        textForHighlighting: String,
        textBeforeIntention: String? = null,
        textAfterIntention: String? = null,
    ) {
        test(gradleVersion, CUSTOM_PROJECT) {
            codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
            testHighlighting(textForHighlighting)

            // if there is a warning, check that an intention exists and can be applied
            if (textForHighlighting.contains(WARNING_START)) {
                assertNotNull(textBeforeIntention) {
                    "Internal assert failure: provide 'textBeforeIntention' text, text with a warning should have a quick fix available"
                }
                assertNotNull(textAfterIntention) {
                    "Internal assert failure: provide 'textAfterIntention' text, text with a warning should have a quick fix available"
                }

                val textForHighlightingStripped = textForHighlighting.replace(WARNING_START, "").replace(WARNING_END, "")
                val textBeforeIntentionStripped = textBeforeIntention.replace("<caret>", "")
                // assert that the text used to check highlighting and the text used to apply the intention on are equal just in case
                Assertions.assertEquals(textForHighlightingStripped, textBeforeIntentionStripped) {
                    "Internal assert failure: textForHighlighting and textBeforeIntention should be similar"
                }

                testIntention(textBeforeIntention, textAfterIntention, "Simplify")
            }
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSingleString(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRegular(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNonLiteralArgument(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            val verRef = "1.0"
            dependencies { 
                implementation(group = "org.gradle", name = "gradle-core", version = verRef)
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfiguration(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            val customConf by configurations.creating {}
            dependencies { 
                customConf$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            val customConf by configurations.creating {}
            dependencies { 
                customConf(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
            }
            """.trimIndent(),
            """
            val customConf by configurations.creating {}
            dependencies { 
                customConf("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfigurationString(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                "customConf"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                "customConf"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
            }
            """.trimIndent(),
            """
            dependencies { 
                "customConf"("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomSourceSet(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                "customSourceSetImplementation"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                "customSourceSetImplementation"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
            }
            """.trimIndent(),
            """
            dependencies { 
                "customSourceSetImplementation"("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testArgumentWithDollarInterpolation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            $$"""
            val verRef = "1.0"
            dependencies { 
                implementation$$WARNING_START(group = "org.gradle", name = "gradle-core", version = "$verRef")$$WARNING_END
            }
            """.trimIndent(),
            $$"""
            val verRef = "1.0"
            dependencies { 
                implementation(group = "org.gradle",<caret> name = "gradle-core", version = "$verRef")
            }
            """.trimIndent(),
            $$"""
            val verRef = "1.0"
            dependencies { 
                implementation("org.gradle:gradle-core:$verRef")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExtraArgument(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "someConf")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoVersionArgument(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START(group = "org.gradle", name = "gradle-core")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(group = "org.gradle",<caret> name = "gradle-core")
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoVersionButAnotherArgument(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation(group = "org.gradle", name = "gradle-core", configuration = "someConf")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testWithBlock(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END {
                    exclude(group = "com.google.guava", module = "guava")
                }
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0") {
                    exclude(group = "com.google.guava", module = "guava")
                }
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0") {
                    exclude(group = "com.google.guava", module = "guava")
                }
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnusualArgumentOrder(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START(version = "1.0", group = "org.gradle", name = "gradle-core")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(version = "1.0",<caret> group = "org.gradle", name = "gradle-core")
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRawStringArguments(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START(group = ${'"'}""org.gradle""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(group = ${'"'}""org.gradle""${'"'},<caret> name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMultiDollarInterpolation(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            $$$"""
            val gradle = "org.gradle"
            dependencies { 
                implementation$$$WARNING_START(group = $$${'"'}""$gradle""$$${'"'}, name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")$$$WARNING_END
            }
            """.trimIndent(),
            $$$"""
            val gradle = "org.gradle"
            dependencies { 
                implementation(group = $$${'"'}""$gradle""$$${'"'},<caret> name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")
            }
            """.trimIndent(),
            $$$"""
            val gradle = "org.gradle"
            dependencies { 
                implementation("$gradle:gr${'$'}dle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPositionalArguments(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START("org.gradle", "gradle-core", "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(<caret>"org.gradle", "gradle-core", "1.0")
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMixPositionalAndNamedArguments(gradleVersion: GradleVersion) {
        runTest(
            gradleVersion,
            """
            dependencies { 
                implementation$WARNING_START("org.gradle", "gradle-core", version = "1.0")$WARNING_END
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation(<caret>"org.gradle", "gradle-core", version = "1.0")
            }
            """.trimIndent(),
            """
            dependencies { 
                implementation("org.gradle:gradle-core:1.0")
            }
            """.trimIndent()
        )
    }

    companion object {
        private const val WARNING_START = "<weak_warning>"
        private const val WARNING_END = "</weak_warning>"
        private val CUSTOM_PROJECT = GradleTestFixtureBuilder.create("avoid_named_arguments") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withJavaPlugin()
                withPrefix {
                    code("val customSourceSet by sourceSets.creating {}")
                    code("val customConf by configurations.creating {}")
                }
            }
        }
    }
}