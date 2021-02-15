// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class GroovyStaticTypeCheckVisitor(private val holder: AnnotationHolder) : GroovyStaticTypeCheckVisitorBase() {

  override fun registerError(location: PsiElement,
                             @InspectionMessage description: String,
                             fixes: Array<LocalQuickFix>?,
                             highlightType: ProblemHighlightType) {
    if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
      return
    }
    var builder = holder.newAnnotation(HighlightSeverity.ERROR, description).range(location)
    if (fixes == null) {
      builder.create()
      return
    }
    for (fix in fixes) {
      builder = builder.withFix(object : IntentionAction {
        override fun getText(): String = fix.name
        override fun getFamilyName(): String = fix.familyName
        override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true
        override fun startInWriteAction(): Boolean = true
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
          val manager = InspectionManager.getInstance(project)
          val descriptor = manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.size == 1, false)
          fix.applyFix(project, descriptor)
        }
      })
    }
    builder.create()
  }
}
