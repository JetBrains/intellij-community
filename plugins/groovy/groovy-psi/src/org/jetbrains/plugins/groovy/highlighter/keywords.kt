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
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

class GroovyKeywordHighlightingPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar)
  : AbstractProjectComponent(project), TextEditorHighlightingPassFactory {

  init {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? = file.getGroovyFile()?.let {
    GroovyKeywordHighlightingPass(it, editor.document)
  }
}

private class GroovyKeywordHighlightingPass(file: GroovyFileBase, document: Document) : GroovyHighlightingPass(file, document), DumbAware {

  override fun doCollectInformation(progress: ProgressIndicator) {
    myFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element.node.elementType in TokenSets.KEYWORDS) {
          if (highlightKeyword(element)) {
            addInfo(element, GroovySyntaxHighlighter.KEYWORD)
          }
        }
        else {
          super.visitElement(element)
        }
      }
    })
  }
}

private fun highlightKeyword(element: PsiElement): Boolean {
  val tokenType = element.node.elementType
  val parent = element.parent

  if (parent is GrArgumentLabel) {
    //don't highlight: print (void:'foo')
    return false
  }
  else if (PsiTreeUtil.getParentOfType(element, GrCodeReferenceElement::class.java) != null) {
    if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(tokenType)) {
      return false //It is allowed to name packages 'as', 'in', 'def' or 'trait'
    }
  }
  else if (tokenType === GroovyTokenTypes.kDEF && element.parent is GrAnnotationNameValuePair) {
    return false
  }
  else if (parent is GrReferenceExpression && element === parent.referenceNameElement) {
    if (tokenType === GroovyTokenTypes.kSUPER && parent.qualifier == null) return true
    if (tokenType === GroovyTokenTypes.kTHIS && parent.qualifier == null) return true
    return false //don't highlight foo.def
  }

  return true
}