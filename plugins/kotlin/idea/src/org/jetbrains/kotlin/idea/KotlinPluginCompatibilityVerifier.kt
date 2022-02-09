// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.ui.Messages
import com.intellij.util.text.nullize

object KotlinPluginCompatibilityVerifier {
    @JvmStatic
    fun checkCompatibility() {
        val kotlinVersion = KotlinPluginVersion.getCurrent() ?: return
        val platformVersion = PlatformVersion.getCurrent() ?: return

        if (kotlinVersion.platformVersion.platform != platformVersion.platform) {
            Messages.showWarningDialog(
                KotlinBundle.message("plugin.verifier.compatibility.issue.message", kotlinVersion, platformVersion),
                KotlinBundle.message("plugin.verifier.compatibility.issue.title")
            )
        }
    }
}

data class KotlinVersionVerbose(val plainVersion: KotlinVersion, val milestone: KotlinVersionMilestone?, val buildNumber: Int?) {
    @Suppress("EnumEntryName")
    enum class KotlinVersionMilestone {
        SNAPSHOT, dev, M1, M2, RC, RC2, release;
    }

    companion object {
        const val REGEX =
            "(\\d+)\\." +
                    "(\\d+)\\." +
                    "(\\d+)" +
                    "(?:-([A-Za-z]+\\d?))?" +
                    "(?:-(\\d+))?"

        @JvmStatic
        fun parse(version: String): KotlinVersionVerbose? {
            val (major, minor, patch, milestone, buildNumber) = REGEX.toRegex().matchEntire(version)
                .let { it ?: return null }.destructured
            return fromRawArgs(major, minor, patch, milestone, buildNumber)
        }

        internal fun fromRawArgs(
            major: String,
            minor: String,
            patch: String,
            milestone: String?,
            buildNumber: String?
        ): KotlinVersionVerbose {
            val enumMilestone = KotlinVersionMilestone.values().singleOrNull { it.name.equals(milestone, ignoreCase = true) }
            val intBuildNumber = buildNumber?.toIntOrNull()
            return KotlinVersionVerbose(KotlinVersion(major.toInt(), minor.toInt(), patch.toInt()), enumMilestone, intBuildNumber)
        }
    }

    override fun toString(): String = buildString {
        append(plainVersion)
        milestone?.let {
            append('-')
            append(it.name)
        }

        buildNumber?.let {
            append('-')
            append(it)
        }
    }
}

/**
 * Tests - [org.jetbrains.kotlin.test.CompatibilityVerifierVersionComparisonTest]
 */
interface KotlinPluginVersion {
    val kotlinVersion: String // 1.2.3
    val status: String?
    val platformVersion: PlatformVersion
    val buildNumber: String?

    companion object {
        fun parse(version: String): KotlinPluginVersion? {
            return OldKotlinPluginVersion.parse(version) ?: NewKotlinPluginVersion.parse(version)
        }

        fun getCurrent(): KotlinPluginVersion? = parse(KotlinPluginUtil.getPluginVersion())
    }
}

data class NewKotlinPluginVersion(
    val kotlinVersionVerbose: KotlinVersionVerbose,
    override val buildNumber: String?, // 53
    override val platformVersion: PlatformVersion,
    val patchNumber: String?
) : KotlinPluginVersion {
    override val kotlinVersion: String
        get() = kotlinVersionVerbose.plainVersion.toString()

    override val status: String?
        get() = kotlinVersionVerbose.milestone?.name

    companion object {
        //203-1.4.20-dev-4575-IJ1234.45-1
        private const val KID_KOTLIN_VERSION_REGEX_STRING =
            "^(\\d{3})" +                               // IDEA_VERSION_ID, like '202'
                    "-${KotlinVersionVerbose.REGEX}" +  // Kotlin Compiler version
                    "-([A-Z]{2})" +                     // Platform kind, like 'IJ'
                    "(?:(\\d+)\\.)?" +                  // (Optional) BRANCH_SUFFIX, like '1234'
                    "(\\d*)" +                          // Build number, like '45'
                    "(?:-(\\d+))?"                      // (Optional) Tooling update, like '-1'

        private val KID_KOTLIN_VERSION_REGEX = KID_KOTLIN_VERSION_REGEX_STRING.toRegex()

        fun parse(version: String): NewKotlinPluginVersion? {
            val matchResult = KID_KOTLIN_VERSION_REGEX.matchEntire(version) ?: return null
            val (ideaVersionId, major, minor, patch,
                kotlinMilestone, kotlinBuildNumber, ideaKind,
                branchSuffix, buildNumber, update) = matchResult.destructured

            val platformVersionString = buildString {
                append(ideaKind)
                append(ideaVersionId)
                branchSuffix.nullize()?.let {
                    append(".")
                    append(it)
                }
            }
            val platformVersion = PlatformVersion.parse(platformVersionString) ?: return null

            return NewKotlinPluginVersion(
                KotlinVersionVerbose.fromRawArgs(major, minor, patch, kotlinMilestone, kotlinBuildNumber) ?: return null,
                buildNumber.nullize(),
                platformVersion,
                update.nullize()
            )
        }
    }
}

data class OldKotlinPluginVersion(
    override val kotlinVersion: String, // 1.2.3
    val milestone: String?, // M1
    override val status: String?, // release, eap, rc
    override val buildNumber: String?, // 53
    override val platformVersion: PlatformVersion,
    val patchNumber: String // usually '1'
) : KotlinPluginVersion {
    companion object {
        private const val KOTLIN_VERSION_REGEX_STRING =
            "^([\\d.]+)" +                // Version number, like 1.3.50
                    "(?:-(M\\d+))?" +     // (Optional) M-release, like M2
                    "(?:-([A-Za-z]+))?" + // (Optional) status, like 'eap/dev/release'
                    "(?:-(\\d+))?" +      // (Optional) buildNumber (absent for 'release')
                    "-([A-Za-z0-9.]+)" +  // Platform version, like Studio4.0.1
                    "-(\\d+)$"            // Tooling update, like '-1'

        private val OLD_KOTLIN_VERSION_REGEX = KOTLIN_VERSION_REGEX_STRING.toRegex()

        fun parse(version: String): OldKotlinPluginVersion? {
            val matchResult = OLD_KOTLIN_VERSION_REGEX.matchEntire(version) ?: return null
            val (kotlinVersion, milestone, status, buildNumber, platformString, patchNumber) = matchResult.destructured
            val platformVersion = PlatformVersion.parse(platformString) ?: return null
            return OldKotlinPluginVersion(
                kotlinVersion,
                milestone.nullize(),
                status.nullize(),
                buildNumber.nullize(),
                platformVersion,
                patchNumber
            )
        }
    }

    override fun toString() = "$kotlinVersion for $platformVersion"
}
