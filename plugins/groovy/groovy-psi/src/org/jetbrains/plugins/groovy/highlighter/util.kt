/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

internal fun PsiFile.getGroovyFile(): GroovyFileBase? = viewProvider.getPsi(GroovyLanguage) as? GroovyFileBase

internal abstract class GroovyHighlightingPass(val myFile: PsiFile, document: Document)
  : TextEditorHighlightingPass(myFile.project, document) {

  protected val myInfos = mutableListOf<HighlightInfo>()

  override fun doApplyInformationToEditor() {
    if (myDocument == null || myInfos.isEmpty()) return
    UpdateHighlightersUtil.setHighlightersToEditor(
      myProject, myDocument, 0, myFile.textLength, myInfos, colorsScheme, id
    )
  }

  protected fun addInfo(element: PsiElement, attribute: TextAttributesKey) {
    val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
    builder.range(element).needsUpdateOnTyping(false).textAttributes(attribute).create()?.let {
      myInfos.add(it)
    }
  }
}