// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveUnexpectedValuesQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.schema.descriptors.getDescriptor

class EditorConfigListAcceptabilityInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitOptionValueList(list: EditorConfigOptionValueList) {
      if (list.getDescriptor(false) != null) return
      list.describableParent?.getDescriptor(false) ?: return
      val message = EditorConfigBundle["inspection.value.list.acceptability.message"]
      holder.registerProblem(list, message, EditorConfigRemoveUnexpectedValuesQuickFix(), EditorConfigRemoveOptionQuickFix())
    }
  }
}
