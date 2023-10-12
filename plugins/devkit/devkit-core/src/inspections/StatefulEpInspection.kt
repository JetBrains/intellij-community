// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.processExtensionsByClassName
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.*

internal class StatefulEpInspection : DevKitUastInspectionBase(UField::class.java, UClass::class.java) {

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val uClass = field.getContainingUClass() ?: return ProblemDescriptor.EMPTY_ARRAY
    val className = uClass.javaPsi.name ?: return ProblemDescriptor.EMPTY_ARRAY

    val isQuickFix by lazy(LazyThreadSafetyMode.NONE) { InheritanceUtil.isInheritor(uClass.javaPsi, localQuickFixFqn) }

    val targets = findEpCandidates(manager.project, className)
    if (targets.isEmpty() && !isQuickFix) {
      return ProblemDescriptor.EMPTY_ARRAY
    }

    val holder = createProblemsHolder(field, manager, isOnTheFly)

    if (isHoldingElement(field.type, PsiElement::class.java.canonicalName)) {
      holder.registerUProblem(field, getMessage(isQuickFix))
      return holder.resultsArray
    }

    if (isHoldingElement(field.type, PsiReference::class.java.canonicalName)) {
      holder.registerUProblem(field, message(PsiReference::class.java.simpleName, isQuickFix))
      return holder.resultsArray
    }

    val fieldTypeClass = PsiTypesUtil.getPsiClass(field.type) ?: return ProblemDescriptor.EMPTY_ARRAY
    if (!isProjectFieldAllowed(field, uClass, targets) && InheritanceUtil.isInheritor(fieldTypeClass, Project::class.java.canonicalName)) {
      holder.registerUProblem(field, message(Project::class.java.simpleName, isQuickFix))
      return holder.resultsArray
    }

    return ProblemDescriptor.EMPTY_ARRAY
  }

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val holder = createProblemsHolder(aClass, manager, isOnTheFly)
    if (canCapture(aClass) && InheritanceUtil.isInheritor(aClass.javaPsi, localQuickFixFqn)) {
      for (capturedElement in getCapturedPoints(aClass)) {
        if (capturedElement.resolved is UVariable && isHoldingElement(capturedElement.resolved.type,
                                                                      PsiElement::class.java.canonicalName)) {
          val capturedElementPsi = capturedElement.reference.sourcePsi ?: continue
          holder.registerProblem(capturedElementPsi, getMessage(true))
        }
      }
    }
    return holder.resultsArray
  }

  private val holderClasses = listOf(java.util.Collection::class.java.canonicalName,
                                     java.util.Map::class.java.canonicalName,
                                     com.intellij.openapi.util.Ref::class.java.canonicalName)

  private fun isHoldingElement(type: PsiType, elementClass: String): Boolean {
    val typeClass = PsiTypesUtil.getPsiClass(type) ?: return false
    if (InheritanceUtil.isInheritor(typeClass, elementClass)) {
      return true
    }
    val typeArguments = (type as? PsiClassType)?.parameters ?: return false
    if (typeArguments.isNotEmpty() && holderClasses.any { InheritanceUtil.isInheritor(typeClass, it) }) {
      return typeArguments.any {
        // Kotlin's List<PsiElement> is List <? extends Element>, so we need to use bound if exists:
        val typeBound = (it as? PsiWildcardType)?.bound
        val actualType = typeBound ?: it
        val actualPsiType = actualType ?: return@any false
        isHoldingElement(actualPsiType, elementClass)
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

  private data class CapturedDescriptor(val reference: UElement, val resolved: UElement)

  private fun getCapturedPoints(uClass: UClass): Collection<CapturedDescriptor> {
    val capturedPoints = mutableListOf<CapturedDescriptor>()
    if (canCapture(uClass)) {
      val argumentList = if (uClass is UAnonymousClass) (uClass.javaPsi as? PsiAnonymousClass)?.argumentList else null
      uClass.accept(object : AbstractUastVisitor() {
        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
          val expressionPsi = node.sourcePsi ?: return super.visitSimpleNameReferenceExpression(node)
          if (argumentList == null || !PsiTreeUtil.isAncestor(argumentList, expressionPsi, true)) {
            val refElement = node.resolveToUElementOfType<UVariable>() ?: return super.visitSimpleNameReferenceExpression(node)
            val containingClass = refElement.getUastParentOfType<UClass>() ?: return super.visitSimpleNameReferenceExpression(node)
            if (isPsiAncestor(containingClass, uClass, true)) {
              capturedPoints.add(CapturedDescriptor(node, refElement))
            }
          }
          return super.visitSimpleNameReferenceExpression(node)
        }
      })
    }
    return capturedPoints
  }

  private fun canCapture(uClass: UClass): Boolean {
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

  private fun isProjectFieldAllowed(field: UField, uClass: UClass, targets: Collection<XmlTag>): Boolean {
    if (field.isFinal) {
      return true
    }

    return targets.any { candidate ->
      val name = candidate.name
      "projectService" == name || "projectConfigurable" == name
    } || InheritanceUtil.isInheritor(uClass.javaPsi, projectComponentFqn)
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  private fun message(what: String, quickFix: Boolean): String {
    if (quickFix) {
      return DevKitBundle.message("inspections.stateful.extension.point.do.not.use.in.quick.fix", what)
    }
    return DevKitBundle.message("inspections.stateful.extension.point.do.not.use.in.extension", what)
  }
}
