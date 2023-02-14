// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersionOrNull
import java.io.Serializable

sealed interface KotlinGradlePluginVersion : Serializable {
    val versionString: String
    val major: Int
    val minor: Int
    val patch: Int

    companion object {
        fun parse(version: String): KotlinGradlePluginVersion? {
            /*
            TODO Sellmair: Remove catch workaround after 1.8.0 release
            https://youtrack.jetbrains.com/issue/KT-54301/KotlinToolingVersionOrNull-IllegalArgumentException
             */
            return try {
                KotlinGradlePluginVersionImpl(KotlinToolingVersionOrNull(version) ?: return null)
            } catch (t: IllegalArgumentException) {
                null
            }
        }
    }
}

fun KotlinGradlePluginVersion.reparse(): KotlinGradlePluginVersion? {
    return KotlinGradlePluginVersion.parse(versionString)
}

operator fun KotlinGradlePluginVersion.compareTo(version: KotlinToolingVersion): Int {
    return this.toKotlinToolingVersion().compareTo(version)
}

operator fun KotlinGradlePluginVersion.compareTo(version: String): Int {
    return this.toKotlinToolingVersion().compareTo(KotlinToolingVersion(version))
}

operator fun KotlinGradlePluginVersion.compareTo(other: KotlinGradlePluginVersion): Int {
    return this.toKotlinToolingVersion().compareTo(other.toKotlinToolingVersion())
}

inline fun <T> KotlinGradlePluginVersion.invokeWhenAtLeast(version: String, action: () -> T): T? {
    return if (this >= version) action() else null
}

inline fun <T> KotlinGradlePluginVersion.invokeWhenAtLeast(version: KotlinGradlePluginVersion, action: () -> T): T? {
    return if (this >= version) action() else null
}

inline fun <T> KotlinGradlePluginVersion.invokeWhenAtLeast(version: KotlinToolingVersion, action: () -> T): T? {
    return if (this >= version) action() else null
}

fun KotlinGradlePluginVersion.toKotlinToolingVersion() = KotlinToolingVersion(versionString)

private class KotlinGradlePluginVersionImpl(
    private val version: KotlinToolingVersion
) : KotlinGradlePluginVersion {
    override val versionString: String get() = version.toString()

    override val major: Int
        get() = version.major

    override val minor: Int
        get() = version.minor

    override val patch: Int
        get() = version.patch

    override fun toString(): String = versionString

    override fun equals(other: Any?): Boolean {
        if (other !is KotlinGradlePluginVersion) return false
        return this.versionString == other.versionString
    }

    override fun hashCode(): Int {
        return version.hashCode()
    }
}
