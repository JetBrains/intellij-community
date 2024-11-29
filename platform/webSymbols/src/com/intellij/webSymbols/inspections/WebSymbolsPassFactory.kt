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

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? =
    if (psiFile.viewProvider.allFiles.any { WebSymbolsHighlightInLanguageEP.shouldHighlight(it.language) })
      WebSymbolsInspectionsPass(psiFile, editor.document)
    else null
}