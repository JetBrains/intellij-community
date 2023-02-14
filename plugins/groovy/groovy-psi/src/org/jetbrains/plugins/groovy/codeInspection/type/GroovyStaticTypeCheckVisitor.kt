// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInspection.isTypecheckingDisabled

class GroovyStaticTypeCheckVisitor(private val holder: AnnotationHolder) : GroovyStaticTypeCheckVisitorBase() {

  override fun registerError(location: PsiElement,
                             @InspectionMessage description: String,
                             fixes: Array<LocalQuickFix>?,
                             highlightType: ProblemHighlightType) {
    if (isTypecheckingDisabled(location.containingFile)) {
      return
    }
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
      return
    }
    var builder = holder.newAnnotation(HighlightSeverity.ERROR, description).range(location)
    if (fixes == null) {
      builder.create()
      return
    }
    val manager = InspectionManager.getInstance(location.project)
    val problemDescriptor = manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.size == 1, false)
    for (fix in fixes) {
      builder = builder.newLocalQuickFix(fix, problemDescriptor).registerFix()
    }
    builder.create()
  }
}
