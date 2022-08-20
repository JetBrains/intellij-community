// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher

object GradleKotlinTestUtils {

    @Deprecated("Use KotlinToolingVersion instead", level = DeprecationLevel.ERROR)
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
        val V_1_4_32 = KotlinToolingVersion(1, 4, 32, null)
        val V_1_5_32 = KotlinToolingVersion(1, 5, 32, null)
        val V_1_6_21 = KotlinToolingVersion(1, 6, 21, null)
        val LAST_SNAPSHOT = KotlinToolingVersion(1, 7, 255, "SNAPSHOT")

        val ALL_PUBLIC = listOf(
            V_1_4_32,
            V_1_5_32,
            V_1_6_21,
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
