// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinVersionUtils")
@file:Suppress("deprecation_error")

package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase.KotlinVersionRequirement
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

fun KotlinVersionRequirement.matches(kotlinVersionString: String): Boolean {
    return matches(KotlinToolingVersion(kotlinVersionString))
}

fun KotlinVersionRequirement.matches(version: KotlinToolingVersion): Boolean {
    return when (this) {
        is KotlinVersionRequirement.Exact -> matches(version)
        is KotlinVersionRequirement.Range -> matches(version)
    }
}

fun KotlinVersionRequirement.Exact.matches(version: KotlinToolingVersion): Boolean {
    return this.version.compareTo(version) == 0
}

fun KotlinVersionRequirement.Range.matches(version: KotlinToolingVersion): Boolean {
    if (lowestIncludedVersion != null && version < lowestIncludedVersion) return false
    if (highestIncludedVersion != null && version > highestIncludedVersion) return false
    return true
}

fun parseKotlinVersionRequirement(value: String): KotlinVersionRequirement {
    if (value.endsWith("+")) {
        return KotlinVersionRequirement.Range(
            lowestIncludedVersion = KotlinToolingVersion(value.removeSuffix("+")),
            highestIncludedVersion = null
        )
    }

    if (value.contains("<=>")) {
        val split = value.split(Regex("""\s*<=>\s*"""))
        require(split.size == 2) { "Illegal Kotlin version requirement: $value. Example: '1.4.0 <=> 1.5.0'" }
        return KotlinVersionRequirement.Range(
            lowestIncludedVersion = KotlinToolingVersion(split[0]),
            highestIncludedVersion = KotlinToolingVersion(split[1])
        )
    }

    return KotlinVersionRequirement.Exact(KotlinToolingVersion(value))
}
