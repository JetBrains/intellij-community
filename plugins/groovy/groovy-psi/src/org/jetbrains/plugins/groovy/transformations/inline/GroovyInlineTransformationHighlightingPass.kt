// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.highlighter.GroovyHighlightingPass
import org.jetbrains.plugins.groovy.highlighter.getGroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor

internal class GroovyInlineTransformationHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? = psiFile.getGroovyFile()?.let {
    GroovyInlineTransformationHighlightingPass(psiFile, it, editor.document)
  }
}

private class GroovyInlineTransformationHighlightingPass(psiFile:PsiFile, val groovyBaseFile: GroovyFileBase, document: Document) : GroovyHighlightingPass(psiFile, groovyBaseFile, document) {
  override fun doCollectInformation(progress: ProgressIndicator) {
    groovyBaseFile.accept(object : GroovyRecursiveElementVisitor() {

      override fun visitElement(element: GroovyPsiElement) {
        val performer = getRootInlineTransformationPerformer(element) ?: return super.visitElement(element)
        val customHighlighting = performer.computeHighlighting()
        for (highlightInfo in customHighlighting) {
          addInfo(highlightInfo)
        }
      }

    })
    applyInformationInBackground()
  }
}