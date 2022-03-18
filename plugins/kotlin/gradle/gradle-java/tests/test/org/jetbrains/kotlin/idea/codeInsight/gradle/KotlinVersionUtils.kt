// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinVersionUtils")

package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.KotlinVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase.KotlinVersionRequirement

val KotlinVersion.isSnapshot: Boolean
    get() = this.classifier != null && this.classifier.toLowerCase() == "snapshot"

val KotlinVersion.isDev: Boolean
    get() = this.classifier != null && this.classifier.matches(Regex("""dev-?\d*"""))

val KotlinVersion.isMilestone: Boolean
    get() = this.classifier != null &&
            this.classifier.matches(Regex("""[mM]-?\d*"""))

val KotlinVersion.isAlpha: Boolean
    get() = this.classifier != null &&
            this.classifier.matches(Regex("""(alpha|ALPHA)-?\d*"""))

val KotlinVersion.isBeta: Boolean
    get() = this.classifier != null &&
            this.classifier.matches(Regex("""(beta|BETA)-?\d*"""))

val KotlinVersion.isRC: Boolean
    get() = this.classifier != null &&
            this.classifier.matches(Regex("""(rc|RC)-?\d*"""))

val KotlinVersion.isWildcard: Boolean
    get() = this.classifier != null &&
            this.classifier == WILDCARD_KOTLIN_VERSION_CLASSIFIER


val KotlinVersion.isStable: Boolean get() = this.classifier == null

val KotlinVersion.isPreRelease: Boolean get() = !isStable


enum class KotlinVersionMaturity {
    WILDCARD,
    UNKNOWN,
    SNAPSHOT,
    DEV,
    MILESTONE,
    ALPHA,
    BETA,
    RC,
    STABLE
}

val KotlinVersion.maturity: KotlinVersionMaturity
    get() = when {
        isStable -> KotlinVersionMaturity.STABLE
        isRC -> KotlinVersionMaturity.RC
        isBeta -> KotlinVersionMaturity.BETA
        isAlpha -> KotlinVersionMaturity.ALPHA
        isMilestone -> KotlinVersionMaturity.MILESTONE
        isSnapshot -> KotlinVersionMaturity.SNAPSHOT
        isDev -> KotlinVersionMaturity.DEV
        isWildcard -> KotlinVersionMaturity.WILDCARD
        else -> KotlinVersionMaturity.UNKNOWN
    }

object KotlinVersionComparator : Comparator<KotlinVersion> {
    override fun compare(o1: KotlinVersion?, o2: KotlinVersion?): Int {
        return o1?.compareTo(o2 ?: return 0) ?: 0
    }
}

operator fun KotlinVersion.compareTo(other: KotlinVersion): Int {
    if (this == other) return 0
    (this.major - other.major).takeIf { it != 0 }?.let { return it }
    (this.minor - other.minor).takeIf { it != 0 }?.let { return it }
    (this.patch - other.patch).takeIf { it != 0 }?.let { return it }
    (this.maturity.ordinal - other.maturity.ordinal).takeIf { it != 0 }?.let { return it }
    if (this.classifier != null && other.classifier != null) {
        val thisLastNumber = Regex("""\d+""").findAll(this.classifier).lastOrNull()?.value?.toIntOrNull()
        val otherLastNumber = Regex("""\d+""").findAll(other.classifier).lastOrNull()?.value?.toIntOrNull()
        if (thisLastNumber != null && otherLastNumber != null) {
            return thisLastNumber - otherLastNumber
        }
    }

    return 0
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

fun parseKotlinVersionOrNull(value: String): KotlinVersion? {
    return try {
        parseKotlinVersionOrNull(value)
    } catch (t: IllegalArgumentException) {
        return null
    }
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
        classifier = classifier?.toLowerCase()
    )
}

private const val WILDCARD_KOTLIN_VERSION_CLASSIFIER = "__*__"

fun KotlinVersion.toWildcard(): KotlinVersion {
    return KotlinVersion(
        major, minor, patch,
        classifier = WILDCARD_KOTLIN_VERSION_CLASSIFIER
    )
}

val KotlinVersion.isHmppEnabledByDefault get() = this >= parseKotlinVersion("1.6.20-dev-6442")