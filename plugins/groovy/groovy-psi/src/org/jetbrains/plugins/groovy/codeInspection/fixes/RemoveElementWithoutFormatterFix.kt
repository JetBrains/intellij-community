// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager

class RemoveElementWithoutFormatterFix(private val familyName: String) : LocalQuickFix {

  override fun getFamilyName(): String = familyName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
      element.delete()
    }
  }
}
