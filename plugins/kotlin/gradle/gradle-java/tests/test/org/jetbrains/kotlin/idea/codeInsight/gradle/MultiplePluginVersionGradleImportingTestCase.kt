// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * This TestCase implements possibility to test import with different versions of gradle and different
 * versions of gradle kotlin plugin
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ProjectInfo
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.LAST_SNAPSHOT
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_4_32
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_5_32
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.TestedKotlinGradlePluginVersions.V_1_6_21
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.runners.Parameterized
import java.io.File


@Suppress("ACCIDENTAL_OVERRIDE")
abstract class MultiplePluginVersionGradleImportingTestCase : KotlinGradleImportingTestCase() {

    sealed class KotlinVersionRequirement {
        data class Exact(val version: KotlinToolingVersion) : KotlinVersionRequirement()
        data class Range(
            val lowestIncludedVersion: KotlinToolingVersion?, val highestIncludedVersion: KotlinToolingVersion?
            ) : KotlinVersionRequirement()
    }

    @Rule
    @JvmField
    var gradleAndKotlinPluginVersionMatchingRule = PluginTargetVersionsRule()

    @JvmField
    @Parameterized.Parameter(1)
    var kotlinPluginParameter: String = ""

    val kotlinPluginVersion: KotlinToolingVersion get() = KotlinToolingVersion(kotlinPluginVersionString)

    open val kotlinPluginVersionString: String get() = if (kotlinPluginParameter == "master") masterKotlinPluginVersion else kotlinPluginParameter


    override fun setUp() {
        if (kotlinPluginVersionString == masterKotlinPluginVersion && IS_UNDER_TEAMCITY) {
            assertTrue("Master version of Kotlin Gradle Plugin is not found in local maven repo", localKotlinGradlePluginExists())
        } else if (kotlinPluginVersionString == masterKotlinPluginVersion) {
            assumeTrue("Master version of Kotlin Gradle Plugin is not found in local maven repo", localKotlinGradlePluginExists())
        }
        super.setUp()
        setupSystemProperties()
    }

    private fun setupSystemProperties() {
        /*
        Commonizer runner forwarded this property and failed, because IntelliJ might set a custom
        ClassLoader, which will not be available for the Commonizer.
        */
        if (kotlinPluginVersion < KotlinToolingVersion("1.5.20")) {
            val classLoaderKey = "java.system.class.loader"
            System.getProperty(classLoaderKey)?.let { configuredClassLoader ->
                System.clearProperty(classLoaderKey)
                Disposer.register(testRootDisposable) {
                    System.setProperty(classLoaderKey, configuredClassLoader)
                }
            }
        }

        val gradleNativeKey = "org.gradle.native"
        System.getProperty(gradleNativeKey).let { configuredGradleNative ->
            System.setProperty(gradleNativeKey, "false")
            Disposer.register(testRootDisposable) {
                if (configuredGradleNative == null) System.clearProperty(gradleNativeKey)
                else System.setProperty(gradleNativeKey, configuredGradleNative)
            }
        }
    }

    companion object {
        val masterKotlinPluginVersion: String = System.getenv("KOTLIN_GRADLE_PLUGIN_VERSION") ?: LAST_SNAPSHOT.toString()
        const val kotlinAndGradleParametersName: String = "Gradle-{0}, KotlinGradlePlugin-{1}"

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = kotlinAndGradleParametersName)
        fun data(): Collection<Array<Any>> {
            return if (IS_UNDER_SAFE_PUSH) listOf(
                /* Safe push tested with latest released versions */
                arrayOf("7.3.3", V_1_6_21.toString())
            ) else listOf(
                /* Progressively updated Gradle and KGP versions */
                arrayOf("6.8.3", V_1_4_32.toString()),
                arrayOf("6.9.2", V_1_5_32.toString()),
                arrayOf("7.3.3", V_1_6_21.toString()),
                arrayOf("7.4.2", "master")
            )
        }
    }

    val androidProperties: Map<String, String>
        get() = mapOf(
            "android_gradle_plugin_version" to "7.0.4",
            "compile_sdk_version" to "30",
            "build_tools_version" to "28.0.3",
        )

    val isHmppEnabledByDefault get() = kotlinPluginVersion.isHmppEnabledByDefault

    protected val hmppProperties: Map<String, String>
        get() = mapOf(
            "enable_hmpp_flags" to enableHmppProperties,
            "disable_hmpp_flags" to disableHmppProperties
        )

    protected val enableHmppProperties: String
        get() = if (isHmppEnabledByDefault) "" else """
            kotlin.mpp.enableGranularSourceSetsMetadata=true
            kotlin.native.enableDependencyPropagation=false
            kotlin.mpp.enableHierarchicalCommonization=true
        """.trimIndent()

    protected val disableHmppProperties: String
        get() = if (isHmppEnabledByDefault) "kotlin.mpp.hierarchicalStructureSupport=false" else ""

    protected fun repositories(useKts: Boolean): String = GradleKotlinTestUtils.listRepositories(useKts, gradleVersion)

    override val defaultProperties: Map<String, String>
        get() = super.defaultProperties.toMutableMap().apply {
            putAll(androidProperties)
            putAll(hmppProperties)
            put("kotlin_plugin_version", kotlinPluginVersionString)
            put("kotlin_plugin_repositories", repositories(false))
            put("kts_kotlin_plugin_repositories", repositories(true))
        }

    protected open fun checkProjectStructure(
        exhaustiveModuleList: Boolean = true,
        exhaustiveSourceSourceRootList: Boolean = true,
        exhaustiveDependencyList: Boolean = true,
        body: ProjectInfo.() -> Unit = {}
    ) {
        org.jetbrains.kotlin.gradle.checkProjectStructure(
            myProject,
            projectPath,
            exhaustiveModuleList,
            exhaustiveSourceSourceRootList,
            exhaustiveDependencyList,
            false,
            body
        )
    }

    fun createHighlightingCheck(
        testLineMarkers: Boolean = true,
        severityLevel: HighlightSeverity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
        correspondingFilePostfix: String = ""
    ): HighlightingCheck {
        return HighlightingCheck(
            project = myTestFixture.project,
            projectPath = projectPath,
            testDataDirectory = testDataDirectory(),
            testLineMarkers = testLineMarkers,
            severityLevel = severityLevel,
            correspondingFilePostfix = correspondingFilePostfix
        )
    }

    fun checkHighlightingOnAllModules() {
        createHighlightingCheck().invokeOnAllModules()
    }
}

fun MultiplePluginVersionGradleImportingTestCase.kotlinPluginVersionMatches(versionRequirement: String): Boolean {
    return parseKotlinVersionRequirement(versionRequirement).matches(kotlinPluginVersion)
}

fun MultiplePluginVersionGradleImportingTestCase.gradleVersionMatches(version: String): Boolean {
    return VersionMatcher(GradleVersion.version(gradleVersion)).isVersionMatch(version, true)
}

private fun localKotlinGradlePluginExists(): Boolean {
    val localKotlinGradlePlugin = File(System.getProperty("user.home"))
        .resolve(".m2/repository")
        .resolve("org/jetbrains/kotlin/kotlin-gradle-plugin/${MultiplePluginVersionGradleImportingTestCase.masterKotlinPluginVersion}")

    return localKotlinGradlePlugin.exists()
}
