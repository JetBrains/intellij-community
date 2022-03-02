// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher

object GradleKotlinTestUtils {

    data class KotlinVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val classifier: String? = null
    ) {

        val maturity: KotlinVersionMaturity = when {
            isStable -> KotlinVersionMaturity.STABLE
            isRC -> KotlinVersionMaturity.RC
            isBeta -> KotlinVersionMaturity.BETA
            isAlpha -> KotlinVersionMaturity.ALPHA
            isMilestone -> KotlinVersionMaturity.MILESTONE
            isSnapshot -> KotlinVersionMaturity.SNAPSHOT
            isDev -> KotlinVersionMaturity.DEV
            isWildcard -> KotlinVersionMaturity.WILDCARD
            else -> throw IllegalArgumentException("Can't infer maturity of KotlinVersion $this")
        }

        override fun toString(): String {
            return "$major.$minor.$patch" + if (classifier != null) "-$classifier" else ""
        }
    }

    object TestedKotlinGradlePluginVersions {
        val V_1_3_30 = KotlinVersion(1, 3, 30)
        val V_1_3_72 = KotlinVersion(1, 3, 72)
        val V_1_4_32 = KotlinVersion(1, 4, 32)
        val V_1_5_31 = KotlinVersion(1, 5, 31)
        val V_1_6_10 = KotlinVersion(1, 6, 10)

        val LAST_SNAPSHOT = KotlinVersion(1, 6, 255, "SNAPSHOT")

        val ALL_PUBLIC = listOf(
            V_1_3_30,
            V_1_3_72,
            V_1_4_32,
            V_1_5_31,
            V_1_6_10
        )
    }

    fun listRepositories(useKts: Boolean, gradleVersion: String): String {

        fun gradleVersionMatches(version: String): Boolean =
            VersionMatcher(GradleVersion.version(gradleVersion)).isVersionMatch(version, true)

        fun MutableList<String>.addUrl(url: String) {
            this += if (useKts) "maven(\"$url\")" else "maven { url '$url' }"
        }

        val repositories = mutableListOf<String>()

        repositories.addUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")
        repositories.add("mavenLocal()")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

        if (!gradleVersionMatches("7.0+")) {
            repositories.addUrl("https://cache-redirector.jetbrains.com/jcenter/")
        }
        return repositories.joinToString("\n")
    }
}
