// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValuePair
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.schema.descriptors.getDescriptor

class EditorConfigPairAcceptabilityInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitOptionValuePair(pair: EditorConfigOptionValuePair) {
      if (pair.getDescriptor(false) != null) return
      pair.describableParent?.getDescriptor(false) ?: return
      val message = EditorConfigBundle["inspection.value.pair.acceptability.message"]
      holder.registerProblem(pair, message, EditorConfigRemoveOptionQuickFix())
    }
  }
}
