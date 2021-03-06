// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.psi.util.isFake

class GrAnnotatorImpl : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = holder.currentAnnotationSession.file
    if (FileIndexFacade.getInstance(file.project).isInLibrarySource(file.virtualFile)) {
      return
    }
    if (element is GroovyPsiElement) {
      element.accept(GroovyAnnotator(holder))
      if (isCompileStatic(element)) {
        element.accept(GroovyStaticTypeCheckVisitor(holder))
      }
    }
    else if (element is PsiComment) {
      val text = element.getText()
      if (text.startsWith("/*") && !text.endsWith("*/")) {
        val range = element.getTextRange()
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("doc.end.expected")).range(
          TextRange.create(range.endOffset - 1, range.endOffset)).create()
      }
    }
    else {
      val parent = element.parent
      if (parent is GrMethod && element == parent.nameIdentifierGroovy) {
        val illegalCharacters = illegalJvmNameSymbols.findAll(parent.name).mapTo(HashSet()) { it.value }
        if (illegalCharacters.isNotEmpty() && !parent.isFake()) {
          val chars = illegalCharacters.joinToString { "'$it'" }
          holder.newAnnotation(HighlightSeverity.WARNING, GroovyBundle.message("illegal.method.name", chars)).create()
        }
        if (parent.returnTypeElementGroovy == null) {
          GroovyAnnotator.checkMethodReturnType(parent, element, holder)
        }
      }
      else if (parent is GrField) {
        if (element == parent.nameIdentifierGroovy) {
          val getters = parent.getters
          for (getter in getters) {
            GroovyAnnotator.checkMethodReturnType(getter, parent.nameIdentifierGroovy, holder)
          }

          val setter = parent.setter
          if (setter != null) {
            GroovyAnnotator.checkMethodReturnType(setter, parent.nameIdentifierGroovy, holder)
          }
        }
      }
    }
  }
}
