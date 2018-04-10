// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.annotations

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyAnnotationArgument
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyAnnotationArgumentList
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyAnnotationArgumentValue

class AddAnnotationValueIntention : Intention() {

  companion object {
    private val myPredicate: PsiElementPredicate

    init {
      val singleArgumentCondition = object : PatternCondition<GrAnnotationArgumentList>("with single argument") {
        override fun accepts(t: GrAnnotationArgumentList, context: ProcessingContext?): Boolean = t.attributes.size == 1
      }
      val argumentList = groovyAnnotationArgumentList.with(singleArgumentCondition)
      val argument = groovyAnnotationArgument.withArgumentName(null).withParent(argumentList)
      val argumentValue = groovyAnnotationArgumentValue.withParent(argument)
      myPredicate = PsiElementPredicate(argumentValue::accepts)
    }
  }

  override fun getElementPredicate(): PsiElementPredicate = myPredicate

  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val value = element as? GrAnnotationMemberValue ?: return
    val pair = element.parent as? GrAnnotationNameValuePair ?: return
    val anchor = value.node
    pair.node.addLeaf(GroovyTokenTypes.mIDENT, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, anchor)
    pair.node.addLeaf(GroovyTokenTypes.mASSIGN, "=", anchor)
    CodeStyleManager.getInstance(project).reformat(pair, true)
  }
}