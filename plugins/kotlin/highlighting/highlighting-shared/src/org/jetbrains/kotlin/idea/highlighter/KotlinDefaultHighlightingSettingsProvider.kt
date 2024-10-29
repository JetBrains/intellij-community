// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

var VirtualFile.isKotlinDecompiledFile: Boolean by NotNullableUserDataProperty(Key.create("IS_KOTLIN_DECOMPILED_FILE"), false)

class KotlinDefaultHighlightingSettingsProvider : DefaultHighlightingSettingProvider(), DumbAware {
    override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
        if (!file.isValid) {
            return null
        }

        val psiFile = file.toPsiFile(project) ?: return null
        return when {
            psiFile is KtFile ->
                when {
                    psiFile.isScript() && ScriptConfigurationsProvider.getInstance(project)?.getScriptConfigurationResult(psiFile) == null ->
                        FileHighlightingSetting.SKIP_HIGHLIGHTING

                    psiFile.isCompiled -> FileHighlightingSetting.SKIP_INSPECTION
                    !KotlinSupportAvailability.isSupported(psiFile) -> FileHighlightingSetting.SKIP_HIGHLIGHTING
                    RootKindFilter.libraryFiles.matches(project, file) -> FileHighlightingSetting.SKIP_INSPECTION
                    else -> null
                }

            file.isKotlinDecompiledFile -> FileHighlightingSetting.SKIP_HIGHLIGHTING
            else -> null
        }
    }
}