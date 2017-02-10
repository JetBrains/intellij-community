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
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

class GroovyDeclarationHighlightingPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar)
  : AbstractProjectComponent(project), TextEditorHighlightingPassFactory {

  init {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? = file.getGroovyFile()?.let {
    GroovyDeclarationHighlightingPass(it, editor.document)
  }
}

private class GroovyDeclarationHighlightingPass(file: GroovyFileBase, document: Document) : GroovyHighlightingPass(file, document) {

  override fun doCollectInformation(progress: ProgressIndicator) {
    myFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is GroovyPsiElement && element !is PsiErrorElement) {
          getDeclarationAttribute(element)?.let {
            addInfo(element, it)
          }
        }
        else if (element is GrReferenceElement<*>) {
          GrHighlightUtil.getDeclarationHighlightingAttribute(element.resolve(), element)?.let {
            addInfo(element.referenceNameElement ?: element, it)
          }
        }
        super.visitElement(element)
      }
    })
  }
}

private fun getDeclarationAttribute(element: PsiElement): TextAttributesKey? {
  val parent = element.parent
  if (parent is GrAnnotation && element.node.elementType === GroovyTokenTypes.mAT) {
    return GroovySyntaxHighlighter.ANNOTATION
  }
  else if (parent is GrAnnotationNameValuePair && parent.nameIdentifierGroovy === element) {
    return GroovySyntaxHighlighter.ANNOTATION_ATTRIBUTE_NAME
  }
  else if (parent is GrCodeReferenceElement) {
    val gParent = parent.parent
    if (gParent is GrAnonymousClassDefinition) {
      if (gParent.baseClassReferenceGroovy === parent) {
        return GroovySyntaxHighlighter.ANONYMOUS_CLASS_NAME
      }
    }
  }

  if (parent !is GrNamedElement || parent.nameIdentifierGroovy !== element) {
    return null
  }

  return GrHighlightUtil.getDeclarationHighlightingAttribute(parent, null)
}
