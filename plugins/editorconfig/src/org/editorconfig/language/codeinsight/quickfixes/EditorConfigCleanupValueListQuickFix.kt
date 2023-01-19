// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.codeinsight.inspections.EditorConfigUnexpectedCommaInspection
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.services.EditorConfigElementFactory

class EditorConfigCleanupValueListQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle.get("quickfix.values.list.cleanup.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val list = descriptor.psiElement?.parent as? EditorConfigOptionValueList ?: return
    val badCommas = EditorConfigUnexpectedCommaInspection.findBadCommas(list)
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
