// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * This TestCase implements possibility to test import with different versions of gradle and different
 * versions of gradle kotlin plugin
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.rd.attach
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ProjectInfo
import org.jetbrains.kotlin.util.*
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.runners.Parameterized
import java.io.File


abstract class MultiplePluginVersionGradleImportingTestCase : KotlinGradleImportingTestCase() {

    sealed class KotlinVersionRequirement {
        data class Exact(val version: KotlinVersion) : KotlinVersionRequirement()
        data class Range(val lowestIncludedVersion: KotlinVersion?, val highestIncludedVersion: KotlinVersion?) : KotlinVersionRequirement()
    }

    data class KotlinVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val classifier: String? = null
    ) {
        override fun toString(): String {
            return "$major.$minor.$patch" + if (classifier != null) "-$classifier" else ""
        }
    }

    @Rule
    @JvmField
    var gradleAndKotlinPluginVersionMatchingRule = PluginTargetVersionsRule()

    @JvmField
    @Parameterized.Parameter(1)
    var kotlinPluginParameter: String = ""

    val kotlinPluginVersion: KotlinVersion get() = parseKotlinVersion(kotlinPluginVersionString)

    open val kotlinPluginVersionString: String get() = if (kotlinPluginParameter == "master") masterKotlinPluginVersion else kotlinPluginParameter


    override fun setUp() {
        if (kotlinPluginVersionString == masterKotlinPluginVersion && IS_UNDER_TEAMCITY) {
           assertTrue("Master version of Kotlin Gradle Plugin is not found in local maven repo", localKotlinGradlePluginExists())
        } else if  (kotlinPluginVersionString == masterKotlinPluginVersion) {
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
        if (kotlinPluginVersion < parseKotlinVersion("1.5.20")) {
            val classLoaderKey = "java.system.class.loader"
            System.getProperty(classLoaderKey)?.let { configuredClassLoader ->
                System.clearProperty(classLoaderKey)
                testRootDisposable.attach {
                    System.setProperty(classLoaderKey, configuredClassLoader)
                }
            }
        }

        val gradleNativeKey = "org.gradle.native"
        System.getProperty(gradleNativeKey).let { configuredGradleNative ->
            System.setProperty(gradleNativeKey, "false")
            testRootDisposable.attach {
                if (configuredGradleNative == null) System.clearProperty(gradleNativeKey)
                else System.setProperty(gradleNativeKey, configuredGradleNative)
            }
        }
    }

    companion object {
        val masterKotlinPluginVersion: String = System.getenv("KOTLIN_GRADLE_PLUGIN_VERSION") ?: "1.6.255-SNAPSHOT"

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: Gradle-{0}, KotlinGradlePlugin-{1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("4.9", "1.3.30"),
                arrayOf("5.6.4", "1.3.72"),
                arrayOf("6.7.1", "1.4.0"),
                arrayOf("6.8.2", "1.4.32"),
                arrayOf("7.0.2", "1.5.10"),
                arrayOf("7.0.2", "1.5.20-RC-238"),
                arrayOf("6.8.2", "master"),
                arrayOf("7.0.2", "master")
            )
        }
    }

    fun androidProperties(): Map<String, String> = mapOf(
        "android_gradle_plugin_version" to "4.0.2",
        "compile_sdk_version" to "30",
        "build_tools_version" to "28.0.3",
    )

    val isHmppEnabledByDefault get() = kotlinPluginVersion.isHmppEnabledByDefault

    fun hmppProperties(): Map<String, String> =
        if (isHmppEnabledByDefault) {
            mapOf(
                "enable_hmpp_flags" to "",
                "disable_hmpp_flags" to "kotlin.mpp.hierarchicalStructureSupport=false"
            )
        } else {
            mapOf(
                "enable_hmpp_flags" to """
                    kotlin.mpp.enableGranularSourceSetsMetadata=true
                    kotlin.native.enableDependencyPropagation=false
                    kotlin.mpp.enableHierarchicalCommonization=true
                """.trimIndent(),
                "disable_hmpp_flags" to ""
            )
        }

    private fun repositories(useKts: Boolean): String {
        val repositories = mutableListOf<String>()

        fun MutableList<String>.addUrl(url: String) {
            this += if (useKts) "maven(\"$url\")" else "maven { url '$url' }"
        }

        repositories.addUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")
        repositories.add("mavenLocal()")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-dev")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlinx")

        if (!gradleVersionMatches("7.0+")) {
            repositories.addUrl("https://cache-redirector.jetbrains.com/jcenter/")
        }
        return repositories.joinToString("\n")
    }

    override val defaultProperties: Map<String, String>
        get() = super.defaultProperties.toMutableMap().apply {
            put("kotlin_plugin_version", kotlinPluginVersionString)
            put("kotlin_plugin_repositories", repositories(false))
            put("kts_kotlin_plugin_repositories", repositories(true))

            putAll(androidProperties())
            putAll(hmppProperties())
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

    fun checkHighligthingOnAllModules() {
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
