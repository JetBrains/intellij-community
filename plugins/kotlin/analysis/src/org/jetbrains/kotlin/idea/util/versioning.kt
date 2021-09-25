// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import kotlin.io.path.exists
import kotlin.io.path.readText

fun isEap(version: String): Boolean {
    return version.contains("rc") || version.contains("eap") || version.contains("-M") || version.contains("RC")
}

fun isDev(version: String): Boolean {
    return version.contains("dev")
}

fun isSnapshot(version: String): Boolean {
    return version.contains("SNAPSHOT", ignoreCase = true)
}

val buildNumber: String by lazy {
    val versionFile = KotlinArtifacts.instance.kotlincDirectory.toPath().resolve("build.txt")
    if (versionFile.exists()) versionFile.readText().trim() else "unknown"
}
