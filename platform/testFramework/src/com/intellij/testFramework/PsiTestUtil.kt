// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.asSafely
import com.intellij.util.text.allOccurrencesOf

@JvmOverloads
fun PsiFile.findReferenceByText(str: String, refOffset: Int = str.length / 2): PsiReference =
  findAllReferencesByText(str, refOffset).firstOrNull() ?: throw AssertionError("can't find reference for '$str'")

@JvmOverloads
fun PsiFile.findAllReferencesByText(str: String, refOffset: Int = str.length / 2): Sequence<PsiReference> {
  var offset = refOffset
  if (offset < 0) {
    offset += str.length
  }
  return this.text.allOccurrencesOf(str).map { index ->
    val pos = index + offset
    val element = findElementAt(pos) ?: throw AssertionError("can't find element for '$str' at $pos")
    element.findReferenceAt(pos - element.startOffset)
    ?: findInInjected(this, pos)
    ?: throw AssertionError("can't find reference for '$str' at $pos")
  }
}

private fun findInInjected(psiFile: PsiFile, pos: Int): PsiReference? {
  val injected = InjectedLanguageManager.getInstance(psiFile.project).findInjectedElementAt(psiFile, pos) ?: return null
  val documentWindow = PsiDocumentManager.getInstance(psiFile.project).getDocument(injected.containingFile).asSafely<DocumentWindow>()
                       ?: return null
  val hostToInjected = documentWindow.hostToInjected(pos)
  return injected.findReferenceAt(hostToInjected - injected.startOffset)
}