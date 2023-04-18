// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmField
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.lang.jvm.types.JvmType
import com.intellij.lang.jvm.util.JvmInheritanceUtil.isInheritor
import com.intellij.lang.jvm.util.JvmUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.processExtensionsByClassName
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.*

class StatefulEpInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project,
                            sink: HighlightSink,
                            isOnTheFly: Boolean): DefaultJvmElementVisitor<Boolean?> = object : DefaultJvmElementVisitor<Boolean?> {

    override fun visitField(field: JvmField): Boolean? {
      val clazz = field.containingClass ?: return null
      val className = clazz.name ?: return null

      val isQuickFix by lazy(LazyThreadSafetyMode.NONE) { isInheritor(clazz, localQuickFixFqn) }

      val targets = findEpCandidates(project, className)
      if (targets.isEmpty() && !isQuickFix) {
        return null
      }

      if (isHoldingElement(field.type, PsiElement::class.java.canonicalName)) {
        val message = getMessage(isQuickFix)
        sink.highlight(message)
        return false
      }

      if (isHoldingElement(field.type, PsiReference::class.java.canonicalName)) {
        sink.highlight(message(PsiReference::class.java.simpleName, isQuickFix))
        return false
      }

      val fieldTypeClass = JvmUtil.resolveClass(field.type as? JvmReferenceType) ?: return null
      if (!isProjectFieldAllowed(field, clazz, targets) && isInheritor(fieldTypeClass, Project::class.java.canonicalName)) {
        sink.highlight(message(Project::class.java.simpleName, isQuickFix))
        return false
      }

      return false
    }

    override fun visitClass(clazz: JvmClass): Boolean? {
      if (canCapture(clazz) && isInheritor(clazz, localQuickFixFqn)) {
        for (capturedElement in getCapturedPoints(clazz)) {
          if (capturedElement.resolved is UVariable && isHoldingElement(capturedElement.resolved.type, PsiElement::class.java.canonicalName)) {
            val capturedElementPsi = capturedElement.reference.sourcePsi ?: continue
            (sink as HighlightSinkImpl).holder.registerProblem(capturedElementPsi, getMessage(true))
          }
        }
      }
      return super.visitClass(clazz)
    }
  }

  private val holderClasses = listOf(java.util.Collection::class.java.canonicalName,
                                     java.util.Map::class.java.canonicalName,
                                     com.intellij.openapi.util.Ref::class.java.canonicalName)

  private fun isHoldingElement(type: JvmType, elementClass: String): Boolean {
    val typeClass = JvmUtil.resolveClass(type as? JvmReferenceType) ?: return false
    if (isInheritor(typeClass, elementClass)) {
      return true
    }
    if (type is JvmReferenceType && type.typeArguments().iterator().hasNext() && holderClasses.any { isInheritor(typeClass, it) }) {
      return type.typeArguments().any {
        // Kotlin's List<PsiElement> is List <? extends Element>, so we need to use bound if exists:
        val typeBound = (it as? PsiWildcardType)?.bound
        val actualType = typeBound ?: it
        isHoldingElement(actualType, elementClass)
      }
    }
    return false
  }

  private fun getMessage(isQuickFix: Boolean): @Nls String {
    var message = DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element")
    if (isQuickFix) {
      message += ". " + DevKitBundle.message("inspections.stateful.extension.point.leak.psi.element.quick.fix")
    }
    return message
  }
}

private data class CapturedDescriptor(val reference: UElement, val resolved: UElement)

private fun getCapturedPoints(clazz: JvmClass): Collection<CapturedDescriptor> {
  val capturedPoints = mutableListOf<CapturedDescriptor>()
  if (canCapture(clazz)) {
    val uClass = (clazz as? PsiElement)?.toUElement() as? UClass ?: return emptyList()
    val argumentList = if (uClass is UAnonymousClass) (uClass.javaPsi as? PsiAnonymousClass)?.argumentList else null
    uClass.accept(object : AbstractUastVisitor() {
      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
        val expressionPsi = node.sourcePsi ?: return super.visitSimpleNameReferenceExpression(node)
        if (argumentList == null || !PsiTreeUtil.isAncestor(argumentList, expressionPsi, true)) {
          val refElement = node.resolveToUElement() as? UVariable ?: return super.visitSimpleNameReferenceExpression(node)
          val containingClass = refElement.getUastParentOfType<UClass>() ?: return super.visitSimpleNameReferenceExpression(node)
          if (isAncestor(containingClass, uClass)) {
            capturedPoints.add(CapturedDescriptor(node, refElement))
          }
        }
        return super.visitSimpleNameReferenceExpression(node)
      }
    })
  }
  return capturedPoints
}

private fun isAncestor(ancestor: UElement, child: UElement): Boolean {
  val ancestorPsi = ancestor.sourcePsi ?: return false
  val childPsi = child.sourcePsi ?: return false
  return PsiTreeUtil.isAncestor(ancestorPsi, childPsi, true)
}

private fun canCapture(clazz: JvmClass): Boolean {
  val uClass = (clazz as? PsiElement)?.toUElement() as? UClass ?: return false
  return uClass.isLocalOrAnonymousClass()
}

private fun UClass.isLocalOrAnonymousClass(): Boolean {
  return this is UAnonymousClass || this.isLocalClass()
}

private fun UClass.isLocalClass(): Boolean {
  val parent = this.uastParent
  if (parent is UDeclarationsExpression && parent.uastParent is UBlockExpression) {
    return true
  }
  return (parent as? UClass)?.isLocalOrAnonymousClass() == true
}

private val localQuickFixFqn = LocalQuickFix::class.java.canonicalName

@Suppress("DEPRECATION")
private val projectComponentFqn = com.intellij.openapi.components.ProjectComponent::class.java.canonicalName

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
