// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

fun KotlinGradlePluginVersion?.supportsKotlinAndroidMultiplatformSourceSetLayoutVersion2(): Boolean {
    if (this == null) return false
    return this >= "1.8.0-dev-1593"
}

fun KotlinGradlePluginVersion?.supportsKotlinAndroidSourceSetInfo(): Boolean {
    if (this == null) return false
    return this >= "1.8.0-dev-1593"
}