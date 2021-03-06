// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.processExtensionsByClassName

class StatefulEpInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project,
                            sink: HighlightSink,
                            isOnTheFly: Boolean): DefaultJvmElementVisitor<Boolean?> = object : DefaultJvmElementVisitor<Boolean?> {

    override fun visitField(field: JvmField): Boolean? {
      val clazz = field.containingClass ?: return null
      val fieldTypeClass = JvmUtil.resolveClass(field.type as? JvmReferenceType) ?: return null

      val isQuickFix by lazy(LazyThreadSafetyMode.NONE) { isInheritor(clazz, localQuickFixFqn) }

      val targets = findEpCandidates(project, clazz)
      if (targets.isEmpty() && !isQuickFix) {
        return null
      }

      if (isInheritor(fieldTypeClass, PsiElement::class.java.canonicalName)) {
        var message = DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element")
        if (isQuickFix) {
          message += " " + DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element.quick.fix")
        }
        sink.highlight(message)
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
}

private val localQuickFixFqn = LocalQuickFix::class.java.canonicalName
private val projectComponentFqn = ProjectComponent::class.java.canonicalName

private fun findEpCandidates(project: Project, clazz: JvmClass): Collection<XmlTag> {
  val name = clazz.name ?: return emptyList()
  val result = SmartList<XmlTag>()
  processExtensionsByClassName(project, name) { tag, _ ->
    val forClass = tag.getAttributeValue("forClass")
    if (forClass == null || !forClass.contains(name)) {
      result.add(tag)
    }
    true
  }
  return result
}

private fun isProjectFieldAllowed(field: JvmField, clazz: JvmClass, targets: Collection<XmlTag>): Boolean {
  if (field.hasModifier(JvmModifier.FINAL)) {
    return true
  }

  return targets.any { candidate ->
    val name = candidate.name
    "projectService" == name || "projectConfigurable" == name
  } || isInheritor(clazz, projectComponentFqn)
}

@Nls(capitalization = Nls.Capitalization.Sentence)
private fun message(what: String, quickFix: Boolean): String {
  if (quickFix) {
    return DevKitBundle.message("inspections.stateful.extension.point.do.not.use.in.quick.fix", what)
  }
  return DevKitBundle.message("inspections.stateful.extension.point.do.not.use.in.extension", what)
}
