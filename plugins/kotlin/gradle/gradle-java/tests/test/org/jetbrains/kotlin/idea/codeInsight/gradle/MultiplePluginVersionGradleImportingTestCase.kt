// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * This TestCase implements possibility to test import with different versions of gradle and different
 * versions of gradle kotlin plugin
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ProjectInfo
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.TestWithKotlinPluginAndGradleVersions
import org.jetbrains.kotlin.gradle.workspace.OrderEntriesChecker
import org.jetbrains.kotlin.gradle.workspace.checkWorkspaceModel
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions.V_1_7_21
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradlePluginVersions.V_1_8_0
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.Rule
import org.junit.runners.Parameterized
import java.io.File

@Suppress("ACCIDENTAL_OVERRIDE")
abstract class MultiplePluginVersionGradleImportingTestCase : KotlinGradleImportingTestCase(), TestWithKotlinPluginAndGradleVersions {

    annotation class AndroidImportingTest

    sealed class KotlinVersionRequirement {
        data class Exact(val version: KotlinToolingVersion) : KotlinVersionRequirement()
        data class Range(
            val lowestIncludedVersion: KotlinToolingVersion?, val highestIncludedVersion: KotlinToolingVersion?
        ) : KotlinVersionRequirement()
    }

    data class KotlinPluginVersionParam(
        val version: KotlinToolingVersion,
        val name: String = version.toString()
    ) {
        override fun toString(): String = name
    }

    @Rule
    @JvmField
    var gradleAndKotlinPluginVersionMatchingRule = PluginTargetVersionsRule()

    @Rule
    @JvmField
    var androidImportingTestRule = AndroidImportingTestRule()

    @JvmField
    @Parameterized.Parameter(1)
    var kotlinPluginVersionParam: KotlinPluginVersionParam? = null

    override val kotlinPluginVersion: KotlinToolingVersion
        get() = checkNotNull(kotlinPluginVersionParam) {
            "Missing 'kotlinPluginVersionParam'"
        }.version

    override val gradleVersion: String
        get() = super.gradleVersion

    override fun setUp() {
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
        const val kotlinAndGradleParametersName: String = "Gradle-{0}, KotlinGradlePlugin-{1}"

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = kotlinAndGradleParametersName)
        fun data(): Collection<Array<Any>> {
            val parameters = mutableListOf<Array<Any>>()

            fun addVersions(
                gradleVersion: String, kotlinVersion: KotlinToolingVersion, kotlinVersionName: String = kotlinVersion.toString()
            ) = parameters.add(arrayOf(gradleVersion, KotlinPluginVersionParam(kotlinVersion, kotlinVersionName)))

            if (!IS_UNDER_SAFE_PUSH) {
                addVersions("7.4.2", V_1_7_21)
                addVersions("7.6", V_1_8_0)
            }

            addVersions(
                "7.6", KotlinGradlePluginVersions.latest,
                "${KotlinGradlePluginVersions.latest.major}.${KotlinGradlePluginVersions.latest.minor}"
            )

            return parameters
        }
    }

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

    protected fun repositories(useKts: Boolean): String = GradleKotlinTestUtils.listRepositories(
        useKts, GradleVersion.version(gradleVersion), kotlinPluginVersion
    )

    override val defaultProperties: Map<String, String>
        get() = super.defaultProperties.toMutableMap().apply {
            putAll(androidImportingTestRule.properties)
            putAll(hmppProperties)
            put("kotlin_plugin_version", kotlinPluginVersion.toString())
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

    fun checkHighlightingOnAllModules(testLineMarkers: Boolean = true) {
        createHighlightingCheck(testLineMarkers).invokeOnAllModules()
    }

    fun checkWorkspaceModel(
        testClassifier: String? = null,
        configure: TestConfiguration.() -> Unit = {}
    ) {
        val testConfiguration = TestConfiguration().apply {
            // Temporary hack for older usages (they were expecting K/N Dist to be leniently folded)
            getConfiguration(OrderEntriesChecker).hideKonanDist = true
            configure()
        }

        checkWorkspaceModel(
            myProject,
            testDataDirectory(),
            myProjectRoot.toNioPath().toFile(),
            kotlinPluginVersion,
            gradleVersion,
            listOf(OrderEntriesChecker),
            testClassifier = testClassifier,
            testConfiguration = testConfiguration
        )
    }
}

fun MultiplePluginVersionGradleImportingTestCase.kotlinPluginVersionMatches(versionRequirement: String): Boolean {
    return parseKotlinVersionRequirement(versionRequirement).matches(kotlinPluginVersion)
}

/**
 * Since 1.8.0, because we no longer support 1.6 jvm target, we are going to merge
 * kotlin-stdlib-jdk[7|8] into kotlin-stdlib. So we won't add the dependency to -jdk[7|8] by default.
 * That was implemented in the kotlin/4441033134a383b718 commit, and then, the logic was temporarily reverted
 * in the kotlin/928e0e7fb8b3 commit
 */
fun MultiplePluginVersionGradleImportingTestCase.isStdlibJdk78AddedByDefault() =
    kotlinPluginVersion >= KotlinToolingVersion("1.5.0-M1")

fun MultiplePluginVersionGradleImportingTestCase.isKgpDependencyResolutionEnabled(): Boolean =
    kotlinPluginVersion >= KotlinToolingVersion("1.8.20-beta-0")

fun MultiplePluginVersionGradleImportingTestCase.gradleVersionMatches(version: String): Boolean {
    return VersionMatcher(GradleVersion.version(gradleVersion)).isVersionMatch(version, true)
}

// for representation differences between KGP-based and non-KGP-based import
fun MultiplePluginVersionGradleImportingTestCase.nativeDistLibraryDependency(
    libraryName: String,
    libraryPlatform: String?
): Regex {
    val namePart = "Kotlin/Native(:| ${kotlinPluginVersion} -) (platform\\.)?$libraryName"
    val platformPart = " \\| $libraryPlatform".takeIf { libraryPlatform != null } ?: ".*"

    return Regex("$namePart$platformPart")
}

fun setKgpImportInGradlePropertiesFile(projectRoot: VirtualFile, enableKgpDependencyResolution: Boolean) {
    VfsUtil.virtualToIoFile(projectRoot).resolve("gradle.properties").apply {
        appendText("\nkotlin.mpp.import.enableKgpDependencyResolution=$enableKgpDependencyResolution")
    }
}
