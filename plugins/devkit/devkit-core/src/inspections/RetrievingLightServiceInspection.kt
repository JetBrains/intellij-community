// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.isInheritorOf
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class RetrievingLightServiceInspection : DevKitUastInspectionBase(UQualifiedReferenceExpression::class.java) {
  private val COMPONENT_MANAGER_FQN = ComponentManager::class.java.canonicalName
  private val COMPONENT_MANAGER_GET_SERVICE: CallMatcher = CallMatcher.anyOf(
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getServiceIfCreated").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS, "boolean"))

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        val toHighlight = node.sourcePsi ?: return true
        if (!COMPONENT_MANAGER_GET_SERVICE.uCallMatches(node.selector as? UCallExpression)) return true
        val uClass = (node.selector.getExpressionType() as? PsiClassType)?.resolve()?.toUElement(UClass::class.java) ?: return true
        if (isInsideGetInstance(node, uClass)) return true
        val serviceAnnotation = uClass.findAnnotation(Service::class.java.canonicalName) ?: return true
        val level = getLevel(serviceAnnotation)
        val receiverType = node.receiver.getExpressionType() ?: return true
        val array = listOf(
          MismatchReceivingChecker(Project::class.java.canonicalName, Level.APP, "inspection.retrieving.light.service.mismatch.for.app.level"),
          MismatchReceivingChecker(Application::class.java.canonicalName, Level.PROJECT,
                                   "inspection.retrieving.light.service.mismatch.for.project.level"))
        val hasError = array.any { it.check(level, receiverType, toHighlight, holder) }
        if (!hasError) checkIfCanBeReplacedWithGetInstance(uClass, receiverType, holder, toHighlight)
        return true
      }
    }, arrayOf(UQualifiedReferenceExpression::class.java))

  data class MismatchReceivingChecker(val retrievingClassName: String,
                                      val level: Level,
                                      val mismatchRetrievingKey: @PropertyKey(resourceBundle = "messages.DevKitBundle") String) {
    fun check(actualLevel: Level, receiverType: PsiType, toHighlight: PsiElement, holder: ProblemsHolder): Boolean {
      if (receiverType.isInheritorOf(retrievingClassName) && actualLevel == level) {
        val retrievingMessage = DevKitBundle.message(mismatchRetrievingKey)
        holder.registerProblem(toHighlight, retrievingMessage)
        return true
      }
      return false
    }
  }

  private fun checkIfCanBeReplacedWithGetInstance(uClass: UClass, receiverType: PsiType, holder: ProblemsHolder, toHighlight: PsiElement) {
    val isApplicationLevelService = receiverType.isInheritorOf(Application::class.java.canonicalName)
    val method = if (isApplicationLevelService) findGetInstanceApplicationLevel(uClass) else findGetInstanceProjectLevel(uClass)
    val qualifiedName = method?.getContainingUClass()?.qualifiedName ?: return
    val serviceName = StringUtil.getShortName(qualifiedName)
    val message = DevKitBundle.message("inspection.retrieving.light.service.can.be.replaced.with", serviceName, method.name)
    holder.registerProblem(toHighlight, message, ReplaceWithGetInstanceCallFix(serviceName, method.name, isApplicationLevelService))
  }

  private fun isInsideGetInstance(node: UExpression, uClass: UClass): Boolean {
    val returnExpr = node.uastParent as? UReturnExpression ?: return false
    val method = returnExpr.jumpTarget as? UMethod ?: return false
    val returnExpression = getReturnExpression(method)
    return method.getContainingDeclaration() == uClass &&
           returnExpression?.sourcePsi === returnExpr.sourcePsi
  }

  private fun getLevel(annotation: UAnnotation): Level {
    val levels = when (val value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
      is UCallExpression ->
        value.valueArguments
          .mapNotNull { it.tryResolve() }
          .filterIsInstance<PsiField>().filter {
            it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName &&
            it.name in listOf(Service.Level.APP.name, Service.Level.PROJECT.name)
          }.map { it.name }

      is UReferenceExpression ->
        value.tryResolve()
          ?.let { it as PsiField }
          ?.takeIf { it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName }
          ?.name
          ?.let { setOf(it) }
        ?: emptySet()

      else -> emptySet()
    }
    return getLevel(levels)
  }

  private fun findGetInstanceProjectLevel(uClass: UClass): UMethod? {
    return uClass.methods
      .filter { it.isStatic }
      .filter { it.visibility == UastVisibility.PUBLIC }
      .filter { it.uastParameters.size == 1 }
      .find {
        val param = it.uastParameters[0]
        if (param.type.canonicalText != Project::class.java.canonicalName) return@find false
        val qualifiedRef = getReturnExpression(it)?.returnExpression as? UQualifiedReferenceExpression ?: return@find false
        COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedRef.selector as? UCallExpression) &&
        (qualifiedRef.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() == param
      }
  }

  private fun findGetInstanceApplicationLevel(uClass: UClass): UMethod? {
    return uClass.methods
      .filter { it.isStatic }
      .filter { it.visibility == UastVisibility.PUBLIC }
      .filter { it.uastParameters.isEmpty() }
      .find {
        val qualifiedRef = getReturnExpression(it)?.returnExpression as? UQualifiedReferenceExpression ?: return@find false
        COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedRef.selector as? UCallExpression) &&
        qualifiedRef.receiver.getExpressionType()?.isInheritorOf(Application::class.java.canonicalName) == true
      }
  }

  private fun getReturnExpression(method: UMethod): UReturnExpression? {
    return (method.uastBody as? UBlockExpression)?.expressions?.singleOrNull() as? UReturnExpression
  }

  class ReplaceWithGetInstanceCallFix(private val serviceName: String,
                                      private val methodName: String,
                                      private val isApplicationLevelService: Boolean) : LocalQuickFix {

    override fun getFamilyName(): String = DevKitBundle.message("inspection.retrieving.light.service.replace.with", serviceName, methodName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val oldCall = descriptor.psiElement.toUElement() as? UQualifiedReferenceExpression ?: return
      val generationPlugin = UastCodeGenerationPlugin.byLanguage(descriptor.psiElement.language) ?: return
      val factory = generationPlugin.getElementFactory(project)
      val serviceName = oldCall.getExpressionType()?.canonicalText ?: return
      val parameters = if (isApplicationLevelService) emptyList() else listOf(oldCall.receiver)
      val newCall = factory.createCallExpression(receiver = factory.createQualifiedReference(serviceName, null),
                                                 methodName = methodName,
                                                 parameters = parameters,
                                                 expectedReturnType = oldCall.getExpressionType(),
                                                 kind = UastCallKind.METHOD_CALL,
                                                 context = null) ?: return
      oldCall.replace(newCall)
    }
  }
}
