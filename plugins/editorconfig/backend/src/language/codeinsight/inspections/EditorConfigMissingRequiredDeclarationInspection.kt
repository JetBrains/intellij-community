// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigAddRequiredDeclarationsQuickFix
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigIdentifierUtil

class EditorConfigMissingRequiredDeclarationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitPsiElement(element: PsiElement) {
      if (element !is EditorConfigDescribableElement) return
      val descriptor = element.getDescriptor(false) as? EditorConfigDeclarationDescriptor ?: return
      val declarations = EditorConfigIdentifierUtil.findDeclarations(element.section, descriptor.id, element.text)
      val manager = EditorConfigOptionDescriptorManager.getInstance(element.project)
      val errors = manager.getRequiredDeclarationDescriptors(descriptor.id).filter { requiredDescriptor ->
        declarations.none { describable ->
          describable.getDescriptor(false) === requiredDescriptor
        }
      }

      val message = when (val errorCount = errors.count()) {
        0 -> return
        1 -> EditorConfigBundle.get("inspection.declaration.missing.singular.message", element.text)
        else -> EditorConfigBundle.get("inspection.declaration.missing.plural.message", element.text, errorCount)
      }
      holder.registerProblem(
        element,
        message,
        EditorConfigRemoveOptionQuickFix(),
        EditorConfigAddRequiredDeclarationsQuickFix(errors, descriptor.id)
      )
    }
  }
}
