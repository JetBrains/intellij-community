// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class GroovyEditorHighlighterProvider : EditorHighlighterProvider {

  override fun getEditorHighlighter(project: Project?,
                                    fileType: FileType,
                                    virtualFile: VirtualFile?,
                                    colors: EditorColorsScheme): EditorHighlighter = GroovyEditorHighlighter(colors)
}
