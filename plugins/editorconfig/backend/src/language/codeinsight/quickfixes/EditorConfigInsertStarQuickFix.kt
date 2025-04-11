// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.services.EditorConfigElementFactory
import org.jetbrains.annotations.Nls

class EditorConfigInsertStarQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.header.insert.star.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val header = descriptor.psiElement as? EditorConfigHeader ?: return
    val factory = EditorConfigElementFactory.getInstance(project)
    val newHeader = factory.createHeader("[*]")
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
      header.replace(newHeader)
    }
  }
}
