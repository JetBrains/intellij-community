// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

var VirtualFile.isKotlinDecompiledFile: Boolean by NotNullableUserDataProperty(Key.create("IS_KOTLIN_DECOMPILED_FILE"), false)

class KotlinDefaultHighlightingSettingsProvider : DefaultHighlightingSettingProvider() {
    override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
        if (!file.isValid) {
            return null
        }

        return when {
            file.toPsiFile(project) !is KtFile -> null
            RootKindFilter.libraryFiles.matches(project, file) -> FileHighlightingSetting.SKIP_INSPECTION
            file.isKotlinDecompiledFile -> FileHighlightingSetting.SKIP_HIGHLIGHTING
            else -> null
        }
    }
}