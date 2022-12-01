// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration.klib

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.NonNls

object KotlinNativeLibraryNameUtil {
    @NonNls
    @IntellijInternalApi
    const val KOTLIN_NATIVE_LIBRARY_PREFIX = "Kotlin/Native"

    @IntellijInternalApi
    const val KOTLIN_NATIVE_LIBRARY_PREFIX_PLUS_SPACE = "$KOTLIN_NATIVE_LIBRARY_PREFIX "

    @NonNls
    @IntellijInternalApi
    const val GRADLE_LIBRARY_PREFIX = "Gradle: "

    private val IDE_LIBRARY_NAME_REGEX = Regex("^$KOTLIN_NATIVE_LIBRARY_PREFIX_PLUS_SPACE([^\\s]+) - ([^\\s]+)( \\[([\\w ,()*]+)])?$")

    // N.B. Returns null if this is not IDE name of Kotlin/Native library.
    fun parseIDELibraryName(ideLibraryName: String): Triple<String, String, String?>? {
        val match = IDE_LIBRARY_NAME_REGEX.matchEntire(ideLibraryName) ?: return null

        val kotlinVersion = match.groups[1]!!.value
        val libraryName = match.groups[2]!!.value
        val platformPart = match.groups[4]?.value

        return Triple(kotlinVersion, libraryName, platformPart)
    }

    fun isGradleLibraryName(ideLibraryName: String) = ideLibraryName.startsWith(GRADLE_LIBRARY_PREFIX)
}
