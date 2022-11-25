// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compiler.configuration

import org.jetbrains.kotlin.config.LanguageVersion

fun LanguageVersion.coerceAtMostVersion(version: Version): LanguageVersion {
    // 1.4.30+ and 1.5.30+ have full support of next language version
    val languageVersion = if (version.major == 1 && (version.minor == 4 || version.minor == 5) && version.patch >= 30) {
        Version.lookup(version.major, version.minor + 1)
    } else {
        version.languageVersion
    }
    return this.coerceAtMost(languageVersion)
}

class Version(val major: Int, val minor: Int, val patch: Int) {
    val languageVersion: LanguageVersion
        get() = lookup(major, minor)

    fun coerceAtMost(languageVersion: LanguageVersion): LanguageVersion {
        // 1.4.30+ and 1.5.30+ have full support of next language version
        val version = if (major == 1 && (minor == 4 || minor == 5) && patch >= 30) {
            lookup(major, minor + 1)
        } else {
            this.languageVersion
        }
        return languageVersion.coerceAtMost(version)
    }

    companion object {
        private val VERSION_REGEX = Regex("(\\d+)\\.(\\d+)(\\.(\\d+).*)?")
        private val CURRENT_VERSION = parse(KotlinPluginLayout.instance.standaloneCompilerVersion)

        internal fun lookup(major: Int, minor: Int) =
            LanguageVersion.values().firstOrNull { it.major == major && it.minor == minor } ?: LanguageVersion.LATEST_STABLE

        fun parse(version: String?): Version {
            version ?: return CURRENT_VERSION
            val matchEntire = VERSION_REGEX.matchEntire(version) ?: return CURRENT_VERSION
            val values = matchEntire.groupValues
            return  Version(values[1].toInt(), values[2].toInt(), values[4].takeIf { it.isNotEmpty() }?.toInt() ?: 0)
        }
    }
}