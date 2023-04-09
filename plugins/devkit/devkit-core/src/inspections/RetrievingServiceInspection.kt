// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.ServiceUtil.LevelType
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class RetrievingServiceInspection : DevKitUastInspectionBase() {
  private val COMPONENT_MANAGER_FQN = ComponentManager::class.java.canonicalName
  private val COMPONENT_MANAGER_GET_SERVICE: CallMatcher = CallMatcher.anyOf(
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS, "boolean"))

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        val toHighlight = node.sourcePsi ?: return true
        if (!COMPONENT_MANAGER_GET_SERVICE.uCallMatches(node.selector as? UCallExpression)) return true
        val uClass = (node.selector.getExpressionType() as? PsiClassType)?.resolve()?.toUElement(UClass::class.java) ?: return true
        val levelType = ServiceUtil.getLevelType(uClass, holder.project)
        val receiverType = node.receiver.getExpressionType() ?: return true
        val array = listOf(
          MismatchReceivingChecker(Project::class.java.canonicalName, LevelType.APP,
                                   "inspection.retrieving.service.mismatch.for.app.level"),
          MismatchReceivingChecker(Application::class.java.canonicalName, LevelType.PROJECT,
                                   "inspection.retrieving.service.mismatch.for.project.level"))
        val hasError = array.any { it.check(levelType, receiverType, toHighlight, holder) }
        if (!hasError) checkIfCanBeReplacedWithGetInstance(uClass, receiverType, holder, node)
        return true
      }
    }, arrayOf(UQualifiedReferenceExpression::class.java))

  data class MismatchReceivingChecker(val retrievingClassName: String,
                                      val levelType: LevelType,
                                      val mismatchRetrievingKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String) {
    fun check(actualLevelType: LevelType, receiverType: PsiType, toHighlight: PsiElement, holder: ProblemsHolder): Boolean {
      if (receiverType.isInheritorOf(retrievingClassName) && actualLevelType == levelType) {
        val retrievingMessage = DevKitBundle.message(mismatchRetrievingKey)
        holder.registerProblem(toHighlight, retrievingMessage)
        return true
      }
      return false
    }
  }

  private fun checkIfCanBeReplacedWithGetInstance(uClass: UClass,
                                                  receiverType: PsiType,
                                                  holder: ProblemsHolder,
                                                  node: UQualifiedReferenceExpression) {
    val isApplicationLevelService = receiverType.isInheritorOf(Application::class.java.canonicalName)
    val returnExpr = node.uastParent as? UReturnExpression
    if (returnExpr != null) {
      val containingMethod = returnExpr.jumpTarget as? UMethod
      if (containingMethod != null) {
        if (isApplicationLevelService && isGetInstanceApplicationLevel(containingMethod)) return
        if (!isApplicationLevelService && isGetInstanceProjectLevel(containingMethod)) return
      }
    }
    val method = if (isApplicationLevelService) findGetInstanceApplicationLevel(uClass) else findGetInstanceProjectLevel(uClass)
    val qualifiedName = method?.getContainingUClass()?.qualifiedName ?: return
    val serviceName = StringUtil.getShortName(qualifiedName)
    val message = DevKitBundle.message("inspection.retrieving.service.can.be.replaced.with", serviceName, method.name)
    holder.registerProblem(node.sourcePsi!!, message, ProblemHighlightType.WEAK_WARNING,
                           ReplaceWithGetInstanceCallFix(serviceName, method.name, isApplicationLevelService))
  }

  private fun findGetInstanceProjectLevel(uClass: UClass): UMethod? {
    return uClass.methods.find { isGetInstanceProjectLevel(it) }
  }

  private fun isGetInstanceProjectLevel(method: UMethod): Boolean {
    if (!(method.isStaticOrJvmStatic &&
          method.visibility == UastVisibility.PUBLIC &&
          method.uastParameters.size == 1)) {
      return false
    }
    val param = method.uastParameters[0]
    if (param.type.canonicalText != Project::class.java.canonicalName) return false
    val qualifiedRef = getReturnExpression(method)?.returnExpression as? UQualifiedReferenceExpression ?: return false
    return COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedRef.selector as? UCallExpression) &&
           (qualifiedRef.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() == param
  }

  private fun findGetInstanceApplicationLevel(uClass: UClass): UMethod? {
    return uClass.methods.find { isGetInstanceApplicationLevel(it) }
  }

  private fun isGetInstanceApplicationLevel(method: UMethod): Boolean {
    if (!(method.isStaticOrJvmStatic &&
          method.visibility == UastVisibility.PUBLIC &&
          method.uastParameters.isEmpty())) {
      return false
    }
    val qualifiedRef = getReturnExpression(method)?.returnExpression as? UQualifiedReferenceExpression ?: return false
    return COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedRef.selector as? UCallExpression) &&
           qualifiedRef.receiver.getExpressionType()?.isInheritorOf(Application::class.java.canonicalName) == true
  }

  private val UMethod.isStaticOrJvmStatic: Boolean
    get() = this.isStatic || this.findAnnotation(JvmStatic::class.java.canonicalName) != null

  private fun getReturnExpression(method: UMethod): UReturnExpression? {
    return (method.uastBody as? UBlockExpression)?.expressions?.singleOrNull() as? UReturnExpression
  }

  class ReplaceWithGetInstanceCallFix(private val serviceName: String,
                                      private val methodName: String,
                                      private val isApplicationLevelService: Boolean) : LocalQuickFix {

    override fun getFamilyName(): String = DevKitBundle.message("inspection.retrieving.service.replace.with", serviceName, methodName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val oldCall = descriptor.psiElement.toUElement() as? UQualifiedReferenceExpression ?: return
      val generationPlugin = UastCodeGenerationPlugin.byLanguage(descriptor.psiElement.language) ?: return
      val factory = generationPlugin.getElementFactory(project)
      val serviceName = oldCall.getExpressionType()?.canonicalText ?: return
      val parameters = if (isApplicationLevelService) emptyList() else listOf(oldCall.receiver)
      val newCall = factory.createCallExpression(receiver = factory.createSimpleReference(serviceName, oldCall.sourcePsi),
                                                 methodName = methodName,
                                                 parameters = parameters,
                                                 expectedReturnType = oldCall.getExpressionType(),
                                                 kind = UastCallKind.METHOD_CALL,
                                                 context = null) ?: return
      oldCall.replace(newCall)
    }
  }
}
