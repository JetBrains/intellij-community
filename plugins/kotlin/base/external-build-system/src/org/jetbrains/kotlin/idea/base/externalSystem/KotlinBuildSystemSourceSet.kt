// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.externalSystem

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsSafe
import java.nio.file.Path

/**
 * Represents information about a Kotlin Source Set from the 'Build System'.
 * Note: Only information which can be available universally in build systems shall be added here.
 */
@IntellijInternalApi
data class KotlinBuildSystemSourceSet(
    /**
     * Name of the Source Set as known to the Build System.
     * Examples:
     * - "commonMain"
     * - "appleMain"
     * - "jvmMain"
     * - "iosX64Main"
     * - "commonTest",
     * - ...
     */
    @NlsSafe val name: String,

    /**
     * Actual file paths which are included into this Source Set
     * Expected to be absolute paths.
     * Examples:
     * - {projectPath}/src/commonMain/kotlin
     * - {projectPath}/src/jvmMain/kotlin
     * - {projectPath}/src/androidMain/kotlin
     * - ...
     */
    val sourceDirectories: List<Path>,
)