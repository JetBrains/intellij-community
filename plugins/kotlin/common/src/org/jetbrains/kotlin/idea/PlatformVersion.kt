// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.PlatformUtils

data class PlatformVersion(val platform: Platform, val version: String /* 3.1 or 2017.3 */) {
    companion object {
        fun parse(platformString: String): PlatformVersion? {
            for (platform in Platform.values()) {
                for (qualifier in platform.qualifiers) {
                    if (platformString.startsWith(qualifier)) {
                        return PlatformVersion(platform, platformString.drop(qualifier.length))
                    }
                }
            }

            return null
        }

        fun getCurrent(): PlatformVersion? {
            val platform = when (PlatformUtils.getPlatformPrefix()) {
                PlatformUtils.IDEA_CE_PREFIX, PlatformUtils.IDEA_PREFIX -> Platform.IDEA
                "AndroidStudio" -> Platform.ANDROID_STUDIO // from 'com.android.tools.idea.IdeInfo'
                else -> return null
            }

            val version = ApplicationInfo.getInstance().run { majorVersion + "." + minorVersion.substringBefore(".") }
            return PlatformVersion(platform, version)
        }

        fun isAndroidStudio(): Boolean = getCurrent()?.platform == Platform.ANDROID_STUDIO
    }

    enum class Platform(val qualifiers: List<String>, val presentableText: String) {
        IDEA(listOf("IJ"), "IDEA"),
        ANDROID_STUDIO(listOf("Studio", "AS"), "Android Studio")
    }

    override fun toString() = platform.presentableText + " " + version
}
