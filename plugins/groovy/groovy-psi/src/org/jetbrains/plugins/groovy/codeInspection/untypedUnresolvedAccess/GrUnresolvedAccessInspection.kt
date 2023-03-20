// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool
import org.jetbrains.plugins.groovy.codeInspection.isTypecheckingDisabled
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessChecker.shouldHighlightAsUnresolved
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isInStaticCompilationContext

class GrUnresolvedAccessInspection : GroovyLocalInspectionTool() {

  @JvmField
  var myHighlightIfGroovyObjectOverridden = true
  @JvmField
  var myHighlightIfMissingMethodsDeclared = true

  override fun getGroovyOptionsPane() = pane(
    checkbox("myHighlightIfGroovyObjectOverridden", GroovyBundle.message("highlight.if.groovy.object.methods.overridden")),
    checkbox("myHighlightIfMissingMethodsDeclared", GroovyBundle.message("highlight.if.missing.methods.declared")))

  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor {
    return Visitor(UnresolvedReferenceInspectionSink(holder))
  }

  private inner class Visitor(private val highlightSink: HighlightSink) : GroovyElementVisitor() {

    override fun visitReferenceExpression(@NotNull referenceExpression: GrReferenceExpression) {
      if (isTypecheckingDisabled(referenceExpression.containingFile)) {
        return
      }
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
