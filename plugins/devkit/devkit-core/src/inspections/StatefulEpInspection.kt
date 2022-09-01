// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.lang.jvm.util.JvmInheritanceUtil.isInheritor
import com.intellij.lang.jvm.util.JvmUtil
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.processExtensionsByClassName
import java.util.*

class StatefulEpInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project,
                            sink: HighlightSink,
                            isOnTheFly: Boolean): DefaultJvmElementVisitor<Boolean?> = object : DefaultJvmElementVisitor<Boolean?> {

    override fun visitField(field: JvmField): Boolean? {
      val clazz = field.containingClass ?: return null
      val className = clazz.name ?: return null
      val fieldTypeClass = JvmUtil.resolveClass(field.type as? JvmReferenceType) ?: return null

      val isQuickFix by lazy(LazyThreadSafetyMode.NONE) { isInheritor(clazz, localQuickFixFqn) }

      val targets = findEpCandidates(project, className)
      if (targets.isEmpty() && !isQuickFix) {
        return null
      }

      if (isInheritor(fieldTypeClass, PsiElement::class.java.canonicalName)) {
        val message = getMessage(isQuickFix)
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

    override fun visitClass(clazz: JvmClass): Boolean? {
      if (canCapture(clazz) && isInheritor (clazz, localQuickFixFqn)) {
        for (capturedElement in getCapturedPoints(clazz)) {
          if (capturedElement.resolved is PsiVariable && InheritanceUtil.isInheritor(capturedElement.resolved.type,
                                                                                     PsiElement::class.java.name)) {
            (sink as HighlightSinkImpl).holder.registerProblem(capturedElement.reference, getMessage(true))
          }
        }
      }
      return super.visitClass(clazz)
    }
  }

  private fun getMessage(isQuickFix: Boolean): @Nls String {
    var message = DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element")
    if (isQuickFix) {
      message += " " + DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element.quick.fix")
    }
    return message
  }
}

data class CapturedDescriptor(val reference: PsiElement, val resolved: PsiElement)

fun getCapturedPoints(clazz: JvmClass): Collection<CapturedDescriptor> {
  val res = mutableListOf<CapturedDescriptor>()
  if (clazz is PsiClass && canCapture(clazz)) {
    val argumentList = if (clazz is PsiAnonymousClass) clazz.argumentList else null
    clazz.accept(object : JavaRecursiveElementWalkingVisitor() {
      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        if (expression.qualifierExpression == null && (argumentList == null || !PsiTreeUtil.isAncestor(argumentList, expression, true))) {
          val refElement = expression.resolve()
          if (refElement is PsiVariable) {
            val containingClass = PsiTreeUtil.getParentOfType(refElement, PsiClass::class.java)
            if (PsiTreeUtil.isAncestor(containingClass, clazz, true)) {
              res.add(CapturedDescriptor(expression, refElement))
            }
          }
        }
      }
    })
  }
  return res
}

private fun canCapture(clazz: JvmClass) = clazz is PsiClass && PsiUtil.isLocalOrAnonymousClass(clazz)

private val localQuickFixFqn = LocalQuickFix::class.java.canonicalName
private val projectComponentFqn = ProjectComponent::class.java.canonicalName

private fun findEpCandidates(project: Project, className: String): Collection<XmlTag> {
  val result = Collections.synchronizedList(SmartList<XmlTag>())
  processExtensionsByClassName(project, className) { tag, _ ->
    if (tag.getAttributeValue("forClass")?.contains(className) != true) {
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
