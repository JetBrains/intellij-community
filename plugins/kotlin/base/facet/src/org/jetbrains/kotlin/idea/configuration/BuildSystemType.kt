// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

enum class BuildSystemType {
    JPS, Gradle, AndroidGradle, Maven
}

interface BuildSystemTypeDetector {
    fun detectBuildSystemType(module: Module): BuildSystemType?

    // null means specific EP can't answer
    fun isMavenizedProject(project: Project): Boolean? = null

    companion object {
        val EP_NAME = ExtensionPointName.create<BuildSystemTypeDetector>("org.jetbrains.kotlin.buildSystemTypeDetector")
    }
}

val Module.buildSystemType: BuildSystemType
    get() {
        return BuildSystemTypeDetector.EP_NAME
            .extensionList
            .firstNotNullOfOrNull { it.detectBuildSystemType(this) }
            ?: BuildSystemType.JPS
    }

val Project.isMavenized: Boolean
    get() {
        return BuildSystemTypeDetector.EP_NAME
            .extensionList
            .firstNotNullOfOrNull { it.isMavenizedProject(this) }
            ?: false
    }
