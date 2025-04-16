// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList
import com.intellij.editorconfig.common.syntax.psi.impl.EditorConfigElementFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.codeinsight.inspections.findBadCommas
import org.jetbrains.annotations.Nls

class EditorConfigCleanupValueListQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.values.list.cleanup.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val list = descriptor.psiElement?.parent as? EditorConfigOptionValueList ?: return
    val badCommas = findBadCommas(list)
    val manager = CodeStyleManager.getInstance(project)
    manager.performActionWithFormatterDisabled {
      badCommas.forEach(PsiElement::delete)
    }

    val factory = EditorConfigElementFactory.getInstance(project)
    val newValue = factory.createAnyValue(list.text)
    manager.reformat(newValue, true)
    manager.performActionWithFormatterDisabled {
      list.replace(newValue)
    }
  }
}
