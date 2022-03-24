// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.JarUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.util.jar.Attributes

class IdeKotlinVersion private constructor(
    @get:NlsSafe val rawVersion: String,
    val kotlinVersion: KotlinVersion,
    val kind: Kind,
    @get:NlsSafe val buildNumber: String?,
    val languageVersion: LanguageVersion,
    val apiVersion: ApiVersion
): Comparable<IdeKotlinVersion> {
    companion object {
        private val KOTLIN_COMPILER_VERSION_PATTERN = (
            "^(\\d+)" + // major
            "\\.(\\d+)" + // minor
            "\\.(\\d+)" + // patch
            "(?:-([A-Za-z]\\w+(?:-release)?))?" + // kind suffix
            "(?:-(\\d+)?)?$" // build number
        ).toRegex(RegexOption.IGNORE_CASE)

        @JvmStatic
        fun get(@NlsSafe rawVersion: String): IdeKotlinVersion {
            return parse(rawVersion).getOrThrow()
        }

        @JvmStatic
        fun opt(@NlsSafe rawVersion: String): IdeKotlinVersion? {
            return parse(rawVersion).getOrNull()
        }

        @JvmStatic
        fun fromKotlinVersion(version: KotlinVersion): IdeKotlinVersion {
            val languageVersion = LanguageVersion.values().first { it.major == version.major && it.minor == version.minor }
            return IdeKotlinVersion(
                rawVersion = version.toString(),
                kotlinVersion = version,
                kind = Kind.Release,
                buildNumber = null,
                languageVersion = languageVersion,
                apiVersion = ApiVersion.createByLanguageVersion(languageVersion)
            )
        }

        @JvmStatic
        fun fromLanguageVersion(languageVersion: LanguageVersion): IdeKotlinVersion {
            return IdeKotlinVersion(
                rawVersion = "${languageVersion.major}.${languageVersion.minor}.0",
                kotlinVersion = KotlinVersion(languageVersion.major, languageVersion.minor, 0),
                kind = Kind.Release,
                buildNumber = null,
                languageVersion = languageVersion,
                apiVersion = ApiVersion.createByLanguageVersion(languageVersion)
            )
        }

        @JvmStatic
        fun fromManifest(jarFile: VirtualFile): IdeKotlinVersion? {
            val ioFile = VfsUtilCore.virtualToIoFile(jarFile)
            val unprocessedVersion = JarUtil.getJarAttribute(ioFile, Attributes.Name.IMPLEMENTATION_VERSION) ?: return null
            // "Implementation-Version" in MANIFEST.MF is sometimes written as '1.5.31-release-548(1.5.31)'
            val rawVersion = unprocessedVersion.substringBefore('(').trim()
            return opt(rawVersion)
        }

        private fun parseKind(kindSuffix: String, vararg prefixes: String, factory: (Int) -> Kind): Kind? {
            for (prefix in prefixes) {
                if (kindSuffix.startsWith(prefix)) {
                    val numberString = kindSuffix.drop(prefix.length).removeSuffix("-release")
                    if (numberString.isEmpty()) {
                        return factory(1)
                    } else {
                        val number = numberString.toIntOrNull() ?: return null
                        return factory(number)
                    }
                }
            }

            error("None of prefixes $prefixes found in kind suffix \"$kindSuffix\"")
        }

        private fun parse(rawVersion: String): Result<IdeKotlinVersion> {
            val matchResult = KOTLIN_COMPILER_VERSION_PATTERN.matchEntire(rawVersion)
                ?: return Result.failure(IllegalArgumentException("Unsupported compiler version: $rawVersion"))

            val majorValue = matchResult.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid major version component: $rawVersion"))

            val minorValue = matchResult.groupValues[2].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid minor version component: $rawVersion"))

            val patchValue = matchResult.groupValues[3].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid patch version component: $rawVersion"))

            val kotlinVersion = KotlinVersion(majorValue, minorValue, patchValue)

            val kindSuffix = matchResult.groupValues[4].toLowerCaseAsciiOnly()

            val kind = when {
                kindSuffix == "release" || kindSuffix == "" -> Kind.Release
                kindSuffix == "dev" -> Kind.Dev
                kindSuffix == "snapshot" || kindSuffix == "local" -> Kind.Snapshot
                kindSuffix.startsWith("rc") -> parseKind(kindSuffix, "rc") { Kind.ReleaseCandidate(it) }
                kindSuffix.startsWith("beta") -> parseKind(kindSuffix, "beta") { Kind.Beta(it) }
                kindSuffix.startsWith("m") || kindSuffix.startsWith("eap") -> parseKind(kindSuffix, "m", "eap") { Kind.Milestone(it) }
                else -> null
            } ?: return Result.failure(IllegalArgumentException("Unsupported version kind suffix: \"$kindSuffix\" ($rawVersion)"))

            val buildNumber = matchResult.groupValues[5].takeIf { it.isNotEmpty() }

            val languageVersion = LanguageVersion.values().firstOrNull { it.major == majorValue && it.minor == minorValue }
                ?: LanguageVersion.FIRST_SUPPORTED

            val apiVersion = ApiVersion.createByLanguageVersion(languageVersion)

            val ideKotlinVersion = IdeKotlinVersion(rawVersion, kotlinVersion, kind, buildNumber, languageVersion, apiVersion)
            return Result.success(ideKotlinVersion)
        }
    }

    sealed class Kind(val artifactSuffix: String?, val requireBuildNumber: Boolean = false) {
        object Release : Kind(artifactSuffix = null)
        data class ReleaseCandidate(val number: Int) : Kind(artifactSuffix = if (number == 1) "RC" else "RC$number")
        data class Beta(val number: Int) : Kind(artifactSuffix = "Beta$number")
        data class Milestone(val number: Int) : Kind(artifactSuffix = "M$number")
        object Dev : Kind(artifactSuffix = "dev", requireBuildNumber = true)
        object Snapshot : Kind(artifactSuffix = "SNAPSHOT", requireBuildNumber = true)

        override fun toString(): String = javaClass.simpleName
    }

    val baseVersion: String
        get() = kotlinVersion.toString()

    val artifactVersion: String
        get() = buildString {
            append(baseVersion)
            if (kind.artifactSuffix != null) {
                append('-').append(kind.artifactSuffix)
                if (kind.requireBuildNumber && buildNumber != null) {
                    append('-').append(buildNumber)
                }
            }
        }

    val isRelease: Boolean
        get() = kind == Kind.Release

    val isPreRelease: Boolean
        get() = !isRelease

    val isDev: Boolean
        get() = kind == Kind.Dev

    val isSnapshot: Boolean
        get() = kind == Kind.Snapshot

    val languageVersionSettings: LanguageVersionSettings
        get() = LanguageVersionSettingsImpl(languageVersion, apiVersion)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherVersion = (other as? IdeKotlinVersion) ?: return false
        return this.rawVersion == otherVersion.rawVersion
    }

    override fun hashCode(): Int {
        return rawVersion.hashCode()
    }

    override fun toString(): String {
        return rawVersion
    }

    override fun compareTo(other: IdeKotlinVersion): Int {
        return VersionComparatorUtil.compare(this.rawVersion, other.rawVersion)
    }

    fun compare(otherRawVersion: String): Int {
        return VersionComparatorUtil.compare(this.rawVersion, otherRawVersion)
    }

    fun compare(other: IdeKotlinVersion): Int {
        return VersionComparatorUtil.compare(this.rawVersion, other.rawVersion)
    }
}