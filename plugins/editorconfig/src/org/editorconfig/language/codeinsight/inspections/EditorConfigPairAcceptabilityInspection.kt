// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOptionValuePair
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigPairAcceptabilityInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitOptionValuePair(pair: EditorConfigOptionValuePair) {
      if (pair.getDescriptor(false) != null) return
      pair.describableParent?.getDescriptor(false) ?: return
      val message = EditorConfigBundle["inspection.value.pair.acceptability.message"]
      holder.registerProblem(pair, message, EditorConfigRemoveOptionQuickFix())
    }
  }
}
