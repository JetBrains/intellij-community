// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

/**
 * [GradleBuildRoot] is a linked gradle build (don't confuse with gradle project and included build).
 * Each [GradleBuildRoot] may have it's own Gradle version, Java home and other settings.
 *
 * Typically, IntelliJ project have no more than one [GradleBuildRoot].
 */
class GradleBuildRoot(
    val externalProjectPath: String,
    val projectRoots: Collection<String>,
    val supportsScriptModelImport: Boolean,
    private val importingStatus: ImportingStatus,
) {
    enum class ImportingStatus {
        IMPORTING, UPDATED
    }

    fun isImportingInProgress(): Boolean {
        return importingStatus != ImportingStatus.UPDATED
    }
}
