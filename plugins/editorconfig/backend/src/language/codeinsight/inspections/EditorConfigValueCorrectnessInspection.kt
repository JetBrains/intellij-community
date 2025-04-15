// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveListValueQuickFix
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.schema.descriptors.getDescriptor

class EditorConfigValueCorrectnessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitOptionValueIdentifier(identifier: EditorConfigOptionValueIdentifier) {
      val parent = identifier.describableParent

      if (identifier.getDescriptor(false) != null) return
      if (parent?.getDescriptor(false) == null) return

      val message = EditorConfigBundle.get("inspection.value.correctness.message", identifier.text)
      val quickfixes =
        if (parent !is EditorConfigOptionValueList) arrayOf(EditorConfigRemoveOptionQuickFix())
        else arrayOf(EditorConfigRemoveListValueQuickFix(), EditorConfigRemoveOptionQuickFix())

      holder.registerProblem(identifier, message, *quickfixes)
    }
  }
}
