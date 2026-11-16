// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION

fun isGradleKotlinScript(virtualFile: VirtualFile): Boolean = virtualFile.nameSequence.endsWith(".$KOTLIN_DSL_SCRIPT_EXTENSION")

const val minimal_gradle_version_supported: String = "6.0"

fun kotlinDslScriptsModelImportSupported(gradleVersion: String): Boolean {
    return GradleVersionUtil.isGradleAtLeast(gradleVersion, minimal_gradle_version_supported)
}
