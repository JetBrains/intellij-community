// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.codeInspection.utils.checkInspectionEnabledByFileType
import org.jetbrains.plugins.groovy.codeInspection.utils.enhanceInspectionToolPanel
import org.jetbrains.plugins.groovy.codeInspection.utils.getFileTypeController
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor

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

  final override fun getOptionsPane(): OptPane {
    val pane = getGroovyOptionsPane()
    return enhanceInspectionToolPanel(this, pane)
  }

  override fun getOptionController(): OptionController {
    return super.getOptionController().onPrefix("fileType", getFileTypeController(explicitlyEnabledFileTypes))
  }

  open fun getGroovyOptionsPane(): OptPane {
    return OptPane.EMPTY
  }
}
