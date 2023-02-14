// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.builtIns

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter

class KotlinSyntaxHighlighterProviderForDecompiledBuiltIns : SyntaxHighlighterProvider {
    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? {
        return if (fileType == KotlinBuiltInFileType) KotlinHighlighter() else null
    }
}
