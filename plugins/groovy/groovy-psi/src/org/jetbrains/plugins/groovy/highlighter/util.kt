// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

internal fun PsiFile.getGroovyFile(): GroovyFileBase? = viewProvider.getPsi(GroovyLanguage) as? GroovyFileBase

internal abstract class GroovyHighlightingPass(val myFile: PsiFile, document: Document)
  : TextEditorHighlightingPass(myFile.project, document) {

  private val myInfos = mutableListOf<HighlightInfo>()

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

internal fun PsiMethod.isMethodWithLiteralName() = this is GrMethod && nameIdentifierGroovy.isStringNameElement()

internal fun GrReferenceElement<*>.isReferenceWithLiteralName() = referenceNameElement.isStringNameElement()

private fun PsiElement?.isStringNameElement() = this?.node?.elementType in TokenSets.STRING_LITERAL_SET

internal fun GrReferenceElement<*>.isAnonymousClassReference(): Boolean {
  return (parent as? GrAnonymousClassDefinition)?.baseClassReferenceGroovy == this
}

internal fun PsiElement.isThisOrSuper() = node?.elementType?.let {
  it == GroovyTokenTypes.kTHIS || it == GroovyTokenTypes.kSUPER
} ?: false
