// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.codeInspection.utils.checkInspectionEnabledByFileType
import org.jetbrains.plugins.groovy.codeInspection.utils.enhanceInspectionToolPanel
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor
import javax.swing.JComponent

abstract class GroovyLocalInspectionTool : LocalInspectionTool() {

  @JvmField
  var explicitlyEnabledFileTypes: MutableSet<String> = HashSet()

  final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : GroovyPsiElementVisitor(buildGroovyVisitor(holder, isOnTheFly)) {
      override fun visitElement(element: PsiElement) {
        if (checkInspectionEnabledByFileType(this@GroovyLocalInspectionTool, element, explicitlyEnabledFileTypes)) {
          return super.visitElement(element)
        }
      }
    }
  }

  abstract fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor

  final override fun createOptionsPanel(): JComponent? {
    val panel = createGroovyOptionsPanel()
    return enhanceInspectionToolPanel(this, explicitlyEnabledFileTypes, panel)
  }

  protected open fun createGroovyOptionsPanel(): JComponent? = null
}
