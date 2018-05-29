// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmField
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.lang.jvm.util.JvmInheritanceUtil.isInheritor
import com.intellij.lang.jvm.util.JvmUtil
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.idea.devkit.util.ExtensionCandidate
import org.jetbrains.idea.devkit.util.ExtensionLocator

class StatefulEpInspection2 : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): DefaultJvmElementVisitor<Boolean?> = object : DefaultJvmElementVisitor<Boolean?> {

    override fun visitField(field: JvmField): Boolean? {
      val clazz = field.containingClass ?: return null
      val fieldTypeClass = JvmUtil.resolveClass(field.type as? JvmReferenceType) ?: return null

      val isQuickFix by lazy(LazyThreadSafetyMode.NONE) { isInheritor(clazz, localQuickFixFqn) }

      val targets = findEpCandidates(project, clazz)
      if (targets.isEmpty() && !isQuickFix) return null

      if (isInheritor(fieldTypeClass, PsiElement::class.java.canonicalName)) {
        sink.highlight(
          "Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead${if (isQuickFix) "; also see LocalQuickFixOnPsiElement" else ""}"
        )
        return false
      }

      if (isInheritor(fieldTypeClass, PsiReference::class.java.canonicalName)) {
        sink.highlight(message(PsiReference::class.java.simpleName, isQuickFix))
        return false
      }

      if (!isProjectFieldAllowed(field, clazz, targets) && isInheritor(fieldTypeClass, Project::class.java.canonicalName)) {
        sink.highlight(message(Project::class.java.simpleName, isQuickFix))
        return false
      }

      return false
    }
  }

  companion object {
    private val localQuickFixFqn = LocalQuickFix::class.java.canonicalName
    private val projectComponentFqn = ProjectComponent::class.java.canonicalName

    private fun findEpCandidates(project: Project, clazz: JvmClass): Collection<ExtensionCandidate> {
      val name = clazz.name ?: return emptyList()
      return ExtensionLocator.byClass(project, clazz).findCandidates().filter { candidate ->
        val forClass = candidate.pointer.element?.getAttributeValue("forClass")
        forClass == null || !forClass.contains(name)
      }
    }

    private fun isProjectFieldAllowed(field: JvmField, clazz: JvmClass, targets: Collection<ExtensionCandidate>): Boolean {
      val finalField = field.hasModifier(JvmModifier.FINAL)
      if (finalField) return true

      val isProjectEP = targets.any { candidate ->
        val name = candidate.pointer.element?.name
        "projectService" == name || "projectConfigurable" == name
      }
      if (isProjectEP) return true

      return isInheritor(clazz, projectComponentFqn)
    }

    private fun message(what: String, quickFix: Boolean): String {
      val where = if (quickFix) "quick fix" else "extension"
      return "Don't use $what as a field in $where"
    }
  }
}
