// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

enum class BuildSystemType {
    JPS, Gradle, AndroidGradle, Maven
}

interface BuildSystemTypeDetector {
    fun detectBuildSystemType(module: Module): BuildSystemType?

    companion object {
        val EP_NAME = ExtensionPointName.create<BuildSystemTypeDetector>("org.jetbrains.kotlin.buildSystemTypeDetector")
    }
}

fun Module.getBuildSystemType(): BuildSystemType = BuildSystemTypeDetector.EP_NAME
    .extensionList
    .firstNotNullOfOrNull { it.detectBuildSystemType(this) }
    ?: BuildSystemType.JPS
