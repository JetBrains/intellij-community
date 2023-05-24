// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

class KotlinIdePluginVersion(
    val rawVersion: String,
    val platformBaselineVersion: String,
    val platformVersion: String,
    val isAndroidStudio: Boolean,
    val kotlinCompilerVersion: IdeKotlinVersion
) {
    companion object {
        private const val ANDROID_STUDIO_PLATFORM_IDENTIFIER = "AS"
        private val PLATFORM_IDENTIFIERS = setOf("IJ", "AC", ANDROID_STUDIO_PLATFORM_IDENTIFIER)

        fun parse(rawVersion: String): Result<KotlinIdePluginVersion> {
            val platformReleaseBranchName = rawVersion.substringBefore('-')

            val platformReleaseBranchInt = platformReleaseBranchName.toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid platform branch name: $rawVersion"))

            if (platformReleaseBranchInt < 100 || platformReleaseBranchInt >= 1000) {
                val message = "Invalid platform branch name, should be in [100; 1000), got $platformReleaseBranchName: $rawVersion"
                return Result.failure(IllegalArgumentException(message))
            }

            val platformVersion = (2000 + platformReleaseBranchInt / 10).toString() + "." + (platformReleaseBranchInt % 10).toString()

            val versionSuffix = parseVersionSuffix(rawVersion)
                ?: return Result.failure(IllegalArgumentException("Cannot find version suffix: $rawVersion"))

            val isAndroidStudio = versionSuffix.startsWith(ANDROID_STUDIO_PLATFORM_IDENTIFIER)

            val rawKotlinVersion = rawVersion.substring(platformReleaseBranchName.length + 1, rawVersion.length - versionSuffix.length - 1)

            val kotlinCompilerVersion = IdeKotlinVersion.parse(rawKotlinVersion)
                .getOrElse { return Result.failure(it) }

            val version = KotlinIdePluginVersion(
                rawVersion,
                platformReleaseBranchName,
                platformVersion,
                isAndroidStudio,
                kotlinCompilerVersion
            )

            return Result.success(version)
        }

        private fun parseVersionSuffix(rawVersion: String): String? {
            val lastIndex = rawVersion.lastIndexOf('-')
            if (lastIndex < 0) return null

            fun isValidSuffix(suffix: String) = PLATFORM_IDENTIFIERS.any { suffix.startsWith(it) }

            val shortSuffix = rawVersion.substring(lastIndex + 1)
            if (isValidSuffix(shortSuffix)) {
                // 213-1.6.21-release-334-IJ6777.52
                return shortSuffix
            }

            val beforeLastIndex = rawVersion.lastIndexOf('-', startIndex = lastIndex - 1)
            if (beforeLastIndex < 0) return null

            val longSuffix = rawVersion.substring(beforeLastIndex + 1)
            if (isValidSuffix(longSuffix)) {
                // 213-1.6.21-release-334-IJ6777.52-1
                return longSuffix
            }

            return null
        }
    }
}