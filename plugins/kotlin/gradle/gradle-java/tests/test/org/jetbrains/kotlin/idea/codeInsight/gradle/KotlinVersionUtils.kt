// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinVersionUtils")

package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.KotlinVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase.KotlinVersionRequirement
import java.util.*
import kotlin.Comparator

val KotlinVersion.isWildcard: Boolean
    get() = this.classifier != null &&
            this.classifier == WILDCARD_KOTLIN_VERSION_CLASSIFIER

val KotlinVersion.isSnapshot: Boolean
    get() = this.classifier != null && this.classifier.lowercase() == "snapshot"

val KotlinVersion.isDev: Boolean
    get() = this.classifier != null && this.classifier.lowercase().matches(Regex("""dev-?\d*"""))

val KotlinVersion.isMilestone: Boolean
    get() = this.classifier != null &&
            this.classifier.lowercase().matches(Regex("""m\d+(-\d*)?"""))

val KotlinVersion.isAlpha: Boolean
    get() = this.classifier != null &&
            this.classifier.lowercase().matches(Regex("""alpha(\d*)?-?\d*"""))

val KotlinVersion.isBeta: Boolean
    get() = this.classifier != null &&
            this.classifier.lowercase().matches(Regex("""beta(\d*)?-?\d*"""))

val KotlinVersion.isRC: Boolean
    get() = this.classifier != null &&
            this.classifier.lowercase().matches(Regex("""(rc)(\d*)?-?\d*"""))

val KotlinVersion.isStable: Boolean
    get() = this.classifier == null ||
            this.classifier.lowercase().matches(Regex("""(release-)?\d+"""))

val KotlinVersion.isPreRelease: Boolean get() = !isStable

enum class KotlinVersionMaturity {
    WILDCARD,
    SNAPSHOT,
    DEV,
    MILESTONE,
    ALPHA,
    BETA,
    RC,
    STABLE
}

operator fun KotlinVersion.compareTo(other: KotlinVersion): Int {
    if (this == other) return 0
    (this.major - other.major).takeIf { it != 0 }?.let { return it }
    (this.minor - other.minor).takeIf { it != 0 }?.let { return it }
    (this.patch - other.patch).takeIf { it != 0 }?.let { return it }
    (this.maturity.ordinal - other.maturity.ordinal).takeIf { it != 0 }?.let { return it }

    if (this.classifier == null && other.classifier != null) {
        /* eg. 1.6.20 > 1.6.20-200 */
        return 1
    }

    if (this.classifier != null && other.classifier == null) {
        /* e.g. 1.6.20-200 < 1.6.20 */
        return -1
    }

    val thisClassifierNumber = this.classifierNumber
    val otherClassifierNumber = other.classifierNumber
    if (thisClassifierNumber != null && otherClassifierNumber != null) {
        (thisClassifierNumber - otherClassifierNumber).takeIf { it != 0 }?.let { return it }
    }

    if (thisClassifierNumber != null && otherClassifierNumber == null) {
        /* e.g. 1.6.20-rc1 > 1.6.20-rc */
        return 1
    }

    if (thisClassifierNumber == null && otherClassifierNumber != null) {
        /* e.g. 1.6.20-rc < 1.6.20-rc1 */
        return -1
    }

    val thisBuildNumber = this.buildNumber
    val otherBuildNumber = other.buildNumber
    if (thisBuildNumber != null && otherBuildNumber != null) {
        (thisBuildNumber - otherBuildNumber).takeIf { it != 0 }?.let { return it }
    }

    if (thisBuildNumber == null && otherBuildNumber != null) {
        /* e.g. 1.6.20-M1 > 1.6.20-M1-200 */
        return 1
    }

    if (thisBuildNumber != null && otherBuildNumber == null) {
        /* e.g. 1.6.20-M1-200 < 1.6.20-M1 */
        return -1
    }

    return 0
}

val KotlinVersion.buildNumber: Int?
    get() {
        if (classifier == null) return null

        /*
        Handle classifiers that only consist of version + build number. This is used for stable releases
        like:
        1.6.20-1
        1.6.20-22
        1.6.
         */
        val buildNumberOnlyClassifierRegex = Regex("\\d+")
        if (buildNumberOnlyClassifierRegex.matches(classifier)) {
            return classifier.toIntOrNull()
        }

        val classifierRegex = Regex("""(.+?)(\d*)?-?(\d*)?""")
        val classifierMatch = classifierRegex.matchEntire(classifier) ?: return null
        return classifierMatch.groupValues.getOrNull(3)?.toIntOrNull()
    }

val KotlinVersion.classifierNumber: Int?
    get() {
        if (classifier == null) return null

        /*
        Classifiers with only a buildNumber assigned
         */
        val buildNumberOnlyClassifierRegex = Regex("\\d+")
        if (buildNumberOnlyClassifierRegex.matches(classifier)) {
            return null
        }


        val classifierRegex = Regex("""(.+?)(\d*)?-?(\d*)?""")
        val classifierMatch = classifierRegex.matchEntire(classifier) ?: return null
        return classifierMatch.groupValues.getOrNull(2)?.toIntOrNull()
    }

fun KotlinVersionRequirement.matches(kotlinVersionString: String): Boolean {
    return matches(parseKotlinVersion(kotlinVersionString))
}

fun KotlinVersionRequirement.matches(version: KotlinVersion): Boolean {
    return when (this) {
        is KotlinVersionRequirement.Exact -> matches(version)
        is KotlinVersionRequirement.Range -> matches(version)
    }
}

fun KotlinVersionRequirement.Exact.matches(version: KotlinVersion): Boolean {
    return this.version == version
}

fun KotlinVersionRequirement.Range.matches(version: KotlinVersion): Boolean {
    if (lowestIncludedVersion != null && version < lowestIncludedVersion) return false
    if (highestIncludedVersion != null && version > highestIncludedVersion) return false
    return true
}

fun parseKotlinVersionRequirement(value: String): KotlinVersionRequirement {
    if (value.endsWith("+")) {
        return KotlinVersionRequirement.Range(
            lowestIncludedVersion = parseKotlinVersion(value.removeSuffix("+")),
            highestIncludedVersion = null
        )
    }

    if (value.contains("<=>")) {
        val split = value.split(Regex("""\s*<=>\s*"""))
        require(split.size == 2) { "Illegal Kotlin version requirement: $value. Example: '1.4.0 <=> 1.5.0'" }
        return KotlinVersionRequirement.Range(
            lowestIncludedVersion = parseKotlinVersion(split[0]),
            highestIncludedVersion = parseKotlinVersion(split[1])
        )
    }

    return KotlinVersionRequirement.Exact(parseKotlinVersion(value))
}

fun parseKotlinVersion(value: String): KotlinVersion {
    fun throwInvalid(): Nothing {
        throw IllegalArgumentException("Invalid Kotlin version: $value")
    }

    val baseVersion = value.split("-", limit = 2)[0]
    val classifier = value.split("-", limit = 2).getOrNull(1)

    val baseVersionSplit = baseVersion.split(".")
    if (!(baseVersionSplit.size == 2 || baseVersionSplit.size == 3)) throwInvalid()

    return KotlinVersion(
        major = baseVersionSplit[0].toIntOrNull() ?: throwInvalid(),
        minor = baseVersionSplit[1].toIntOrNull() ?: throwInvalid(),
        patch = baseVersionSplit.getOrNull(2)?.let { it.toIntOrNull() ?: throwInvalid() } ?: 0,
        classifier = classifier?.lowercase()
    )
}

private const val WILDCARD_KOTLIN_VERSION_CLASSIFIER = "*"

fun KotlinVersion.toWildcard(): KotlinVersion {
    return KotlinVersion(
        major, minor, patch,
        classifier = WILDCARD_KOTLIN_VERSION_CLASSIFIER
    )
}

val KotlinVersion.isHmppEnabledByDefault get() = this >= parseKotlinVersion("1.6.20-dev-6442")
