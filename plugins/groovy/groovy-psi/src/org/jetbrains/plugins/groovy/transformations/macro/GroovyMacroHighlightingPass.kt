// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.highlighter.GroovyHighlightingPass
import org.jetbrains.plugins.groovy.highlighter.getGroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

internal class GroovyMacroHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? = file.getGroovyFile()?.let {
    GroovyMacroHighlightingPass(it, editor.document)
  }
}

private class GroovyMacroHighlightingPass(val file: GroovyFileBase, document: Document) : GroovyHighlightingPass(file, document) {

  override fun doCollectInformation(progress: ProgressIndicator) {
    file.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitMethodCall(call: GrMethodCall) {
        progress.checkCanceled()
        val customHighlighting = getAvailableMacroSupport(call)?.getHighlighing(call)?.takeUnless(List<HighlightInfo>::isEmpty)
        if (customHighlighting == null) {
          return super.visitMethodCall(call)
        }
        // todo: caching
        for (highlightingInfo in customHighlighting) {
          addInfo(highlightingInfo)
        }
      }
    })
  }
}