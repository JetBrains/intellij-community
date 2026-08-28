// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.ExtensionCandidate
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.util.isInstanceCheck
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Inspection that detects unsafe casts of services declared as `open="true"` to specific subclasses.
 *
 * When a service is marked as "open", it can be overridden by other plugins. Therefore, code
 * should not assume a specific implementation class, as it may lead to ClassCastException at runtime.
 *
 * This inspection detects explicit casts (e.g., `service as MyServiceImpl` or `(MyServiceImpl) service`)
 * regardless of how the open service value was obtained (getService, getInstance, parameters, fields, etc.).
 */
internal class UnsafeOpenServiceCastInspection : DevKitUastInspectionBase() {

  /**
   * Map of open service interfaces to their allowed cast targets.
   */
  private val ignored = mapOf<String, Set<String>>(
    // for example:
    /*"com.intellij.openapi.project.ProjectManager" to setOf(
      "com.intellij.openapi.project.ex.ProjectManagerEx"
    )*/
  )

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
        // Skip safe casts (as?) - they return null instead of ClassCastException
        if (node.isInstanceCheck() || node.operationKind.name == "as?") return true

        val operandClass = (node.operand.getExpressionType() as? PsiClassType)?.resolve() ?: return true
        if (isOpenService(operandClass, holder.project)) {
          val targetClass = (node.type as? PsiClassType)?.resolve() ?: return true
          if (!isIgnored(operandClass, targetClass) && isUnsafeCast(operandClass, targetClass)) {
            val sourcePsi = node.sourcePsi ?: return true
            val serviceClassName = operandClass.name ?: return true
            holder.registerProblem(sourcePsi, DevKitBundle.message("inspection.unsafe.open.service.cast.message", serviceClassName))
          }
        }
        return true
      }
    }, arrayOf(UBinaryExpressionWithType::class.java))
  }

  private fun isOpenService(serviceClass: PsiClass, project: Project): Boolean {
    val domManager = DomManager.getDomManager(project)
    for (candidate in locateExtensionsByPsiClass(serviceClass)) {
      val extension = getServiceExtensionDeclaration(candidate, domManager) ?: continue
      if (isOpenServiceDeclaration(extension)) {
        return true
      }
    }
    return false
  }

  private fun getServiceExtensionDeclaration(candidate: ExtensionCandidate, domManager: DomManager): Extension? {
    val tag = candidate.pointer.element ?: return null
    val extension = domManager.getDomElement(tag) as? Extension ?: return null
    if (!ExtensionUtil.hasServiceBeanFqn(extension)) return null
    return extension
  }

  private fun isOpenServiceDeclaration(extension: Extension): Boolean {
    val openAttribute = DevKitDomUtil.getAttribute(extension, "open")
    return openAttribute != null && openAttribute.stringValue == "true"
  }

  private fun isIgnored(serviceInterface: PsiClass, targetClass: PsiClass): Boolean {
    val ignoredForServiceInterface = ignored[serviceInterface.qualifiedName] ?: return false
    return targetClass.qualifiedName in ignoredForServiceInterface
  }

  private fun isUnsafeCast(serviceInterface: PsiClass, targetClass: PsiClass): Boolean {
    if (serviceInterface.qualifiedName == targetClass.qualifiedName) return false
    return InheritanceUtil.isInheritorOrSelf(targetClass, serviceInterface, true)
  }
}
