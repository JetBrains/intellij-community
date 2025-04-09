// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigAddRequiredDeclarationsQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.psi.reference.EditorConfigIdentifierReference
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigIdentifierUtil

class EditorConfigReferenceCorrectnessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitPsiElement(element: PsiElement) {
      if (element !is EditorConfigDescribableElement) return
      val descriptor = element.getDescriptor(false) as? EditorConfigReferenceDescriptor ?: return
      val reference = element.reference as? EditorConfigIdentifierReference ?: return
      if (reference.multiResolve(false).isNotEmpty()) return

      val sameTextDescriptors =
        EditorConfigIdentifierUtil
          .findDeclarations(element.section, text = element.text)
          .map { it.getDescriptor(false) as EditorConfigDeclarationDescriptor }

      if (sameTextDescriptors.isEmpty()) {
        val message = EditorConfigBundle["inspection.reference.unresolved.message"]
        val possibleDescriptors = getDeclarationDescriptors(descriptor.id, element.project)
        holder.registerProblem(
          element,
          message,
          ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
          EditorConfigAddRequiredDeclarationsQuickFix(possibleDescriptors, descriptor.id)
        )
      }
      else {
        val typeMessage = getTypeMessage(sameTextDescriptors)
        val message = EditorConfigBundle.get("inspection.reference.type.mismatch.message", descriptor.id, typeMessage)
        holder.registerProblem(element, message)
      }
    }
  }

  private fun getDeclarationDescriptors(id: String, project: Project): List<EditorConfigDeclarationDescriptor> {
    val manager = EditorConfigOptionDescriptorManager.getInstance(project)
    val required = manager.getRequiredDeclarationDescriptors(id)
    if (required.isNotEmpty()) return required
    return manager.getDeclarationDescriptors(id)
  }

  private fun getTypeMessage(declarations: List<EditorConfigDeclarationDescriptor>): String {
    if (declarations.isEmpty()) throw IllegalArgumentException()
    if (declarations.size == 1) return declarations.single().id

    fun append(builder: StringBuilder, descriptor: EditorConfigDeclarationDescriptor): StringBuilder {
      builder.append(", ")
      return builder.append(descriptor.id)
    }

    val result = declarations.drop(1).dropLast(1).fold(StringBuilder(declarations.first().id), ::append)
    result.append(" or ").append(declarations.last().id)
    return result.toString()
  }
}
