// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.getDescriptor

class EditorConfigKeyCorrectnessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitQualifiedOptionKey(key: EditorConfigQualifiedOptionKey) = checkKey(key)
    override fun visitFlatOptionKey(key: EditorConfigFlatOptionKey) = checkKey(key)
    fun checkKey(key: EditorConfigDescribableElement) {
      if (key.getDescriptor(false) != null) return
      val message = EditorConfigBundle["inspection.key.correctness.message"]
      holder.registerProblem(key, message, EditorConfigRemoveOptionQuickFix())
    }
  }
}
