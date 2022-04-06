// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.ui.Messages
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

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

        fun getCurrent(): KotlinPluginVersion? = parse(KotlinIdePlugin.version)
    }
}

data class NewKotlinPluginVersion(
    val kotlinCompilerVersion: IdeKotlinVersion,
    override val buildNumber: String?, // 53
    override val platformVersion: PlatformVersion,
    val patchNumber: String?
) : KotlinPluginVersion {
    override val kotlinVersion: String
        get() = kotlinCompilerVersion.kotlinVersion.toString()

    override val status: String?
        get() = kotlinCompilerVersion.kind.artifactSuffix

    companion object {
        // typical version is `203-1.4.20-dev-4575-IJ1234.45-1`.
        // But the regex covers remainder after compiler substring version: `-IJ1234.45-1`
        private const val PLATFORM_SPECIFICATION_SUBSTRING_REGEX_STRING =
            "-([A-Z]{2})" +                     // Platform kind, like 'IJ'
                    "(?:(\\d+)\\.)?" +                  // (Optional) BRANCH_SUFFIX, like '1234'
                    "(\\d*)" +                          // Build number, like '45'
                    "(?:-(\\d+))?"                      // (Optional) Tooling update, like '-1'

        private val PLATFORM_SPECIFICATION_REGEX = PLATFORM_SPECIFICATION_SUBSTRING_REGEX_STRING.toRegex()

        fun parse(version: String): NewKotlinPluginVersion? {
            if (!version.contains("-")) return null
            val ideaVersionId = version.substringBefore("-")
            val remainingVersion = version.substringAfter("-")

            val compilerVersionAndPlatformSpecificationIndexSplitter = remainingVersion.indices.reversed()
                .asSequence()
                .filter { remainingVersion[it] == '-' }
                .firstOrNull { IdeKotlinVersion.opt(remainingVersion.substring(0, it)) != null }
                ?: return null

            val compilerVersion = IdeKotlinVersion.get(remainingVersion.substring(0, compilerVersionAndPlatformSpecificationIndexSplitter))
            val platformSpecification = remainingVersion.substring(compilerVersionAndPlatformSpecificationIndexSplitter)

            val matchResult = PLATFORM_SPECIFICATION_REGEX.matchEntire(platformSpecification) ?: return null
            val (ideaKind, branchSuffix, buildNumber, update) = matchResult.destructured

            val platformVersionString = buildString {
                append(ideaKind)
                append(ideaVersionId)
                branchSuffix.nullize()?.let {
                    append(".")
                    append(it)
                }
            }
            val platformVersion = PlatformVersion.parse(platformVersionString) ?: return null

            return NewKotlinPluginVersion(compilerVersion, buildNumber.nullize(), platformVersion, update.nullize())
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
