// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter

internal class KotlinSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory(), SyntaxHighlighterProvider {
    override fun createHighlighter(): SyntaxHighlighter = KotlinHighlighter()

    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? =
      when (fileType) {
          KotlinBuiltInFileType, KlibMetaFileType -> getSyntaxHighlighter(project, file)
          else -> null
      }

}