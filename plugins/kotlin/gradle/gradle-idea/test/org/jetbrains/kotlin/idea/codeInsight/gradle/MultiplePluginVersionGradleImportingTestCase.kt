// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * This TestCase implements possibility to test import with different versions of gradle and different
 * versions of gradle kotlin plugin
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ProjectInfo
import org.jetbrains.kotlin.util.matches
import org.jetbrains.kotlin.util.parseKotlinVersion
import org.jetbrains.kotlin.util.parseKotlinVersionRequirement
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.runners.Parameterized
import java.io.File


abstract class MultiplePluginVersionGradleImportingTestCase : KotlinGradleImportingTestCase() {

    sealed class KotlinVersionRequirement {
        data class Exact(val version: KotlinVersion) : KotlinVersionRequirement()
        data class Range(val lowestIncludedVersion: KotlinVersion?, val highestIncludedVersion: KotlinVersion?) : KotlinVersionRequirement()
        companion object {
            fun fromString(versionRequirement: String): KotlinVersionRequirement = parseKotlinVersionRequirement(versionRequirement)
        }
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

        companion object {
            fun fromString(version: String): KotlinVersion = parseKotlinVersion(version)
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

    private val orgGradleNativePropertyKey: String = "org.gradle.native"
    private var initialOrgGradleNativePropertyValue: String? = null

    override fun setUp() {
        if (kotlinPluginVersionString == masterKotlinPluginVersion) {
            assumeTrue("Master version of Kotlin Gradle Plugin is not found in local maven repo", localKotlinGradlePluginExists())
        }
        super.setUp()
        initialOrgGradleNativePropertyValue = System.getProperty(orgGradleNativePropertyKey, "false")
        System.setProperty("org.gradle.native", "false")
    }

    override fun tearDown() {
        super.tearDown()
        val initialOrgGradleNativePropertyValue = initialOrgGradleNativePropertyValue
        if (initialOrgGradleNativePropertyValue == null) {
            System.clearProperty(orgGradleNativePropertyKey)
        } else {
            System.setProperty(orgGradleNativePropertyKey, initialOrgGradleNativePropertyValue)
        }
    }

    companion object {
        val masterKotlinPluginVersion: String = System.getenv("KOTLIN_GRADLE_PLUGIN_VERSION") ?: "1.5.255-SNAPSHOT"

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: Gradle-{0}, KotlinGradlePlugin-{1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("4.9", "1.3.30"),
                arrayOf("5.6.4", "1.3.72"),
                arrayOf("6.7.1", "1.4.0"),
                arrayOf("6.8.2", "1.4.32"),
                arrayOf("6.8.2", "1.5.0"),
                arrayOf("7.0", "1.5.0"),
                arrayOf("6.8.2", "master"),
                arrayOf("7.0", "master")
            )
        }
    }

    private fun repositories(useKts: Boolean): String {
        val repositories = mutableListOf(
            "mavenCentral()",
            "mavenLocal()",
            "google()",
            "gradlePluginPortal()"
        )

        if (!gradleVersionMatches("7.0+")) {
            repositories.add("jcenter()")
        }

        fun addCustomRepository(url: String) {
            repositories += if (useKts) "maven(\"$url\")" else "maven { url '$url' }"
        }

        addCustomRepository("https://dl.bintray.com/kotlin/kotlin-dev")
        addCustomRepository("https://kotlin.bintray.com/kotlinx")

        return repositories.joinToString("\n")
    }

    override fun configureByFiles(properties: Map<String, String>?): List<VirtualFile> {
        val unitedProperties = HashMap(properties ?: emptyMap())
        unitedProperties["kotlin_plugin_version"] = kotlinPluginVersionString

        unitedProperties["kotlin_plugin_repositories"] = repositories(false)
        unitedProperties["kts_kotlin_plugin_repositories"] = repositories(true)
        return super.configureByFiles(unitedProperties)
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
