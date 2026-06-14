// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.shared.CachedConfigurationInputs
import org.jetbrains.kotlin.psi.KtFile

/**
 * Gradle script configuration is out of date when essential [sections] are changed.
 * See [getGradleScriptInputsStamp].
 */
data class GradleKotlinScriptConfigurationInputs(
    val sections: String,
) : CachedConfigurationInputs {
    override fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile?): Boolean {
        val actualStamp = getGradleScriptInputsStamp(project, file, ktFile) ?: return false

        return actualStamp.sections == this.sections
    }
}
