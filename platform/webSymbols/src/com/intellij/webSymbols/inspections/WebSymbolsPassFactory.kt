package com.intellij.webSymbols.inspections

import com.intellij.codeHighlighting.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.webSymbols.inspections.impl.WebSymbolsHighlightInLanguageEP
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class WebSymbolsPassFactory() : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, intArrayOf(Pass.LOCAL_INSPECTIONS), true, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? =
    if (file.viewProvider.allFiles.any { WebSymbolsHighlightInLanguageEP.shouldHighlight(it.language) })
      WebSymbolsInspectionsPass(file, editor.document)
    else null
}