// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.isSnapshot
import org.jetbrains.kotlin.tooling.core.isStable
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

object GradleKotlinTestUtils {

    fun listRepositories(useKts: Boolean, gradleVersion: String, kotlinVersion: String? = null) =
        listRepositories(useKts, GradleVersion.version(gradleVersion), kotlinVersion?.let(::KotlinToolingVersion))

    fun listRepositories(useKts: Boolean, gradleVersion: GradleVersion, kotlinVersion: KotlinToolingVersion? = null): String {
        if (useKts && kotlinVersion != null)
            return listKtsRepositoriesOptimized(gradleVersion, kotlinVersion)

        fun gradleVersionMatches(version: String): Boolean =
            VersionMatcher(gradleVersion).isVersionMatch(version, true)

        fun MutableList<String>.addUrl(url: String) {
            this += if (useKts) "maven(\"$url\")" else "maven { url '$url' }"
        }

        val repositories = mutableListOf<String>()

        repositories.add("mavenLocal()")
        repositories.addUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        repositories.addUrl("https://cache-redirector.jetbrains.com/intellij-dependencies")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")

        if (!gradleVersionMatches("7.0+")) {
            repositories.addUrl("https://cache-redirector.jetbrains.com/jcenter/")
        }
        return repositories.joinToString("\n")
    }

    fun setupRemoteRepositoriesForJps(project: Project) {
        RemoteRepositoriesConfiguration.getInstance(project).repositories = listOf(
            RemoteRepositoryDescription(
                "central",
                "Maven Central repository",
                "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/"
            ),
            RemoteRepositoryDescription(
                "bootstrap",
                "Jetbrains Bootstrap Repository",
                "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/"
            ),
            RemoteRepositoryDescription(
                "experimental",
                "experimental repository for dev versions",
                "https://packages.jetbrains.team/maven/p/kt/experimental"
            )
        )
    }

    private fun MutableList<String>.addMavenLocal(kotlinContentConstraint: String) {
        add(
            repository("mavenLocal") { kotlinContentConstraint }
        )
    }

    private fun listKtsRepositoriesOptimized(
        gradleVersion: GradleVersion,
        kotlinVersion: KotlinToolingVersion,
    ): String {
        val repositories = buildList {
            val kotlinContentConstraint = kotlinContentConstraint(kotlinVersion)

            if (kotlinVersion.shouldUseMavenLocal()) {
                addMavenLocal(kotlinContentConstraint)
            }

            if (!kotlinVersion.isStable) {
                val kotlinSnapshotPath = Path(PlatformTestUtil.getCommunityPath()) / "lib" / "kotlin-snapshot"
                when {
                    kotlinGradlePluginExists(kotlinSnapshotPath, kotlinVersion) -> {
                        addMavenRepository(
                            kotlinSnapshotPath.toString(),
                            kotlinContentConstraint
                        )
                        add(KOTLIN_NATIVE_RELEASES_REPOSITORY)
                        add(KOTLIN_NATIVE_DEV_REPOSITORY)
                    }

                    kotlinVersion.isDev -> {
                        addMavenRepository("https://packages.jetbrains.team/maven/p/kt/experimental", kotlinContentConstraint)
                    }
                }

                if (kotlinGradlePluginExists(Path(System.getProperty("user.home")).resolve(".m2/repository"), kotlinVersion)) {
                    addMavenLocal(kotlinContentConstraint)
                } else {
                    addMavenRepository(
                        "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap",
                        kotlinContentConstraint
                    )
                    addMavenRepository("https://cache-redirector.jetbrains.com/intellij-dependencies", kotlinContentConstraint)
                }
            }

            addMavenRepository("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")
            addMavenRepository(
                url = "https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/",
                kotlinContentConstraint =
                    """
                    content {
                        includeGroupByRegex(".*android.*")
                        includeGroupByRegex(".*google.*")
                    }
                    """.trimIndent()
            )
            addMavenRepository("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")

            if (!VersionMatcher(gradleVersion).isVersionMatch("7.0+", true)) {
                addMavenRepository("https://cache-redirector.jetbrains.com/jcenter/")
            }
        }

        return repositories.joinToString("\n")
    }

    private fun MutableList<String>.addMavenRepository(url: String, kotlinContentConstraint: String? = null) {
        val urlLine = """url = uri("$url")"""

        val mavenRepository = repository("maven") {
            buildString {
                append(urlLine)
                kotlinContentConstraint?.let {
                    appendLine()
                    append(it)
                }
            }
        }
        add(mavenRepository)
    }

    private fun KotlinToolingVersion.shouldUseMavenLocal(): Boolean =
        isSnapshot || isStable && classifier != null

    private fun kotlinContentConstraint(kotlinVersion: KotlinToolingVersion): String = """
        content {
            includeVersionByRegex(".*jetbrains.*", ".*", "$kotlinVersion")
        }
        """.trimIndent()

    private fun repository(
        name: String,
        content: (() -> String)? = null,
    ): String = buildString {
        appendLine("$name {")
        content?.invoke()?.let {
            appendLine(it.prependIndent())
        }
        append("}")
    }

    private val KOTLIN_NATIVE_RELEASES_REPOSITORY = kotlinNativeRepository(
        "https://download.jetbrains.com/kotlin/native/builds/releases"
    )

    private val KOTLIN_NATIVE_DEV_REPOSITORY = kotlinNativeRepository(
        "https://download.jetbrains.com/kotlin/native/builds/dev"
    )

    private fun kotlinNativeRepository(url: String): String = """
        ivy {
            url = uri("$url")
            patternLayout {
                artifact("[revision]/[classifier]/[artifact]-[classifier]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
        }
        """.trimIndent()


    private fun kotlinGradlePluginExists(root: Path, version: KotlinToolingVersion, ): Boolean =
        (root / "org/jetbrains/kotlin/kotlin-gradle-plugin/$version").exists()
}