// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isFake

class GrAnnotatorImpl : Annotator {

  private val myTypeCheckVisitor = GroovyStaticTypeCheckVisitor()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = holder.currentAnnotationSession.file
    if (FileIndexFacade.getInstance(file.project).isInLibrarySource(file.virtualFile)) {
      return
    }
    if (element is GroovyPsiElement) {
      element.accept(GroovyAnnotator(holder))
      if (PsiUtil.isCompileStatic(element)) {
        myTypeCheckVisitor.accept(element, holder)
      }
    }
    else if (element is PsiComment) {
      val text = element.getText()
      if (text.startsWith("/*") && !text.endsWith("*/")) {
        val range = element.getTextRange()
        holder.createErrorAnnotation(TextRange.create(range.endOffset - 1, range.endOffset), GroovyBundle.message("doc.end.expected"))
      }
    }
    else {
      val parent = element.parent
      if (parent is GrMethod && element == parent.nameIdentifierGroovy) {
        val illegalCharacters = illegalJvmNameSymbols.findAll(parent.name).mapTo(HashSet()) { it.value }
        if (illegalCharacters.isNotEmpty() && !parent.isFake()) {
          val chars = illegalCharacters.joinToString { "'$it'" }
          holder.createWarningAnnotation(element, GroovyBundle.message("illegal.method.name", chars))
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
