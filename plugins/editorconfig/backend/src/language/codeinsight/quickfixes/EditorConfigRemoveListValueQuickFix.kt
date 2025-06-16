// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList
import com.intellij.editorconfig.common.syntax.psi.impl.EditorConfigElementFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.jetbrains.annotations.Nls

class EditorConfigRemoveListValueQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.value.remove.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val list = element.parent as? EditorConfigOptionValueList ?: return

    val globalRange = EditorConfigPsiTreeUtil.findRemovableRange(element)
    val listOffset = list.textOffset
    val parentRange = globalRange.start - listOffset..globalRange.endInclusive - listOffset

    val text: CharSequence = list.text
    val newText = text.removeRange(parentRange)

    val factory = EditorConfigElementFactory.getInstance(project)
    val newList = factory.createAnyValue(newText)

    val manager = CodeStyleManager.getInstance(project)
    manager.performActionWithFormatterDisabled {
      list.replace(newList)
    }
  }
}
