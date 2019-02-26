// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessChecker.shouldHighlightAsUnresolved
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isInStaticCompilationContext
import javax.swing.JComponent

class GrUnresolvedAccessInspection : GroovySuppressableInspectionTool() {

  @JvmField
  var myHighlightIfGroovyObjectOverridden = true
  @JvmField
  var myHighlightIfMissingMethodsDeclared = true

  override fun createOptionsPanel(): JComponent? {
    val optionsPanel = MultipleCheckboxOptionsPanel(this)
    optionsPanel.addCheckbox(message("highlight.if.groovy.object.methods.overridden"), "myHighlightIfGroovyObjectOverridden")
    optionsPanel.addCheckbox(message("highlight.if.missing.methods.declared"), "myHighlightIfMissingMethodsDeclared")
    return optionsPanel
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return GroovyPsiElementVisitor(Visitor(UnresolvedReferenceInspectionSink(holder)))
  }

  private inner class Visitor(private val highlightSink: HighlightSink) : GroovyElementVisitor() {

    override fun visitReferenceExpression(@NotNull referenceExpression: GrReferenceExpression) {
      if (isInStaticCompilationContext(referenceExpression)) {
        return
      }
      if (!shouldHighlightAsUnresolved(referenceExpression)) {
        return
      }
      checkUnresolvedReference(
        referenceExpression,
        myHighlightIfGroovyObjectOverridden,
        myHighlightIfMissingMethodsDeclared,
        highlightSink
      )
    }
  }

  companion object {

    private const val SHORT_NAME = "GrUnresolvedAccess"

    fun getHighlightDisplayLevel(element: PsiElement): HighlightDisplayLevel {
      val key = HighlightDisplayKey.find(SHORT_NAME) ?: error("Cannot find inspection key")
      val inspectionProfile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
      return inspectionProfile.getErrorLevel(key, element)
    }
  }
}
