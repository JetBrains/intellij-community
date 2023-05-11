// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal abstract class GroovyHighlightingPass(val myFile: PsiFile, document: Document)
  : TextEditorHighlightingPass(myFile.project, document) {

  private val myInfos = mutableListOf<HighlightInfo>()

  override fun doApplyInformationToEditor() {
  }

  internal fun applyInformationInBackground() {
    if (myInfos.isEmpty()) return
    BackgroundUpdateHighlightersUtil.setHighlightersToEditor(
      myProject, myDocument, 0, myFile.textLength, myInfos, colorsScheme, id
    )
  }

  protected fun addInfo(element: PsiElement, attribute: TextAttributesKey) {
    val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
    builder.range(element).needsUpdateOnTyping(false).textAttributes(attribute).create()?.let {
      myInfos.add(it)
    }
  }

  protected fun addInfo(info : HighlightInfo) {
    myInfos.add(info)
  }
}