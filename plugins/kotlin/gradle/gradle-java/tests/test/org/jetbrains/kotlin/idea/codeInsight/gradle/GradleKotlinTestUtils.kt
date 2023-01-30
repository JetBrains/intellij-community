// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isSnapshot
import org.jetbrains.kotlin.tooling.core.isStable
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import java.io.File

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
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")

        if (!gradleVersionMatches("7.0+")) {
            repositories.addUrl("https://cache-redirector.jetbrains.com/jcenter/")
        }
        return repositories.joinToString("\n")
    }

    private fun listKtsRepositoriesOptimized(gradleVersion: GradleVersion, kotlinVersion: KotlinToolingVersion): String {
        val repositories = mutableListOf<String>()
        operator fun String.unaryPlus() = repositories.add(this)

        val mavenLocal = """
            mavenLocal {
                content {
                    includeVersionByRegex(".*jetbrains.*", ".*", "$kotlinVersion")
                }
            }
        """.trimIndent()

        if (kotlinVersion.isSnapshot) {
            +mavenLocal
        }

        if (!kotlinVersion.isStable) {
            if (localKotlinGradlePluginExists(kotlinVersion)) {
                +mavenLocal
            } else +"""
                maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") {
                    content {
                        includeVersionByRegex(".*jetbrains.*", ".*", "$kotlinVersion")
                    }
                }
                
                /* Repository used to resolve manual deployments of KGP (specifically to be resolved by IJ import tests) */
                maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies") {
                    content {
                        includeVersionByRegex(".*jetbrains.*", ".*", "$kotlinVersion")
                    }
                }
            """.trimIndent()
        }

        +"""maven("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")"""

        +"""
            maven("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/") {
                content {
                    includeGroupByRegex(".*android.*")
                    includeGroupByRegex(".*google.*")
                }
            }
            """
            .trimIndent()

        +"""maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")"""

        if (!VersionMatcher(gradleVersion).isVersionMatch("7.0+", true)) {
            +"""maven("https://cache-redirector.jetbrains.com/jcenter/")"""
        }

        return repositories.joinToString("\n")
    }
}

private fun localKotlinGradlePluginExists(kotlinGradlePluginVersion: KotlinToolingVersion): Boolean {
    val localKotlinGradlePlugin = File(System.getProperty("user.home"))
        .resolve(".m2/repository")
        .resolve("org/jetbrains/kotlin/kotlin-gradle-plugin/$kotlinGradlePluginVersion")

    return localKotlinGradlePlugin.exists()
}
