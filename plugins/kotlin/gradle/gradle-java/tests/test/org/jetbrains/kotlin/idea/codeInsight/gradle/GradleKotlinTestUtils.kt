// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher

object GradleKotlinTestUtils {

    fun listRepositories(useKts: Boolean, gradleVersion: String): String {
        fun gradleVersionMatches(version: String): Boolean =
            VersionMatcher(GradleVersion.version(gradleVersion)).isVersionMatch(version, true)

        fun MutableList<String>.addUrl(url: String) {
            this += if (useKts) "maven(\"$url\")" else "maven { url '$url' }"
        }

        val repositories = mutableListOf<String>()

        repositories.add("mavenLocal()")
        repositories.addUrl("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        repositories.addUrl("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        repositories.addUrl("https://cache-redirector.jetbrains.com/dl.google.com.android.maven2/")
        repositories.addUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2/")

        if (!gradleVersionMatches("7.0+")) {
            repositories.addUrl("https://cache-redirector.jetbrains.com/jcenter/")
        }
        return repositories.joinToString("\n")
    }
}
