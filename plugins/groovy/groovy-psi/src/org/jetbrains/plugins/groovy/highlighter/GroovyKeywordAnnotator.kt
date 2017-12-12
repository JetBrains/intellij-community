// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

/**
 * Groovy allows keywords to appear in various places such as FQNs, reference names, labels, etc.
 * Syntax highlighter highlihgts all of them since it's based on lexer, which has no clue which keyword is really a keyword.
 * This knowledge becomes available only after parsing.
 *
 * This annotator clears text attributes for elements which are not really keywords.
 */
class GroovyKeywordAnnotator : Annotator, DumbAware {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (shouldBeErased(element)) {
      holder.createInfoAnnotation(element, null).enforcedTextAttributes = TextAttributes.ERASE_MARKER
    }
  }
}

fun shouldBeErased(element: PsiElement): Boolean {
  val tokenType = element.node.elementType
  if (tokenType !in TokenSets.KEYWORDS) return false // do not touch other elements

  val parent = element.parent
  if (parent is GrArgumentLabel) {
    // don't highlight: print (void:'foo')
    return true
  }
  else if (PsiTreeUtil.getParentOfType(element, GrCodeReferenceElement::class.java) != null) {
    if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(tokenType)) {
      return true // it is allowed to name packages 'as', 'in', 'def' or 'trait'
    }
  }
  else if (tokenType === GroovyTokenTypes.kDEF && element.parent is GrAnnotationNameValuePair) {
    return true
  }
  else if (parent is GrReferenceExpression && element === parent.referenceNameElement) {
    if (tokenType === GroovyTokenTypes.kSUPER && parent.qualifier == null) return false
    if (tokenType === GroovyTokenTypes.kTHIS && parent.qualifier == null) return false
    return true // don't highlight foo.def
  }

  return false
}
