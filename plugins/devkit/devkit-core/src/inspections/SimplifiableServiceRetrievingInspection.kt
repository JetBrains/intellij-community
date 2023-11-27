// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class SimplifiableServiceRetrievingInspection : ServiceRetrievingInspectionBase() {

  override val additionalComponentManagerMethodNames
    get() = emptyArray<String>()

  override val additionalServiceKtFileMethodNames
    get() = emptyArray<String>()

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitCallExpression(node: UCallExpression): Boolean {
        val (howServiceRetrieved, serviceClass) = getServiceRetrievingInfo(node) ?: return true
        val retrievingExpression = getRetrievingExpression(node) ?: return true
        val getInstanceMethod = findGetInstanceMethod(retrievingExpression, howServiceRetrieved, serviceClass)
        if (getInstanceMethod != null) {
          registerProblem(getInstanceMethod, howServiceRetrieved, holder, retrievingExpression)
        }
        return true
      }
    }, arrayOf(UCallExpression::class.java))
  }

  private fun findGetInstanceMethod(retrievingExpression: UExpression,
                                    howServiceRetrieved: Service.Level,
                                    serviceClass: UClass): UMethod? {
    val returnExpr = retrievingExpression.uastParent as? UReturnExpression
    val containingMethod = returnExpr?.jumpTarget as? UMethod
    if (containingMethod != null) {
      if (howServiceRetrieved == Service.Level.APP && isGetInstanceApplicationLevel(containingMethod)) return null
      if (howServiceRetrieved == Service.Level.PROJECT && isGetInstanceProjectLevel(containingMethod)) return null
    }
    return serviceClass.methods.find {
      it.sourcePsi !== containingMethod?.sourcePsi && when (howServiceRetrieved) {
        Service.Level.APP -> isGetInstanceApplicationLevel(it)
        Service.Level.PROJECT -> isGetInstanceProjectLevel(it)
      }
    }?.takeIf { method -> method.returnType == retrievingExpression.getExpressionType()  }
  }

  private fun registerProblem(replacementMethod: UMethod,
                              howServiceRetrieved: Service.Level,
                              holder: ProblemsHolder,
                              retrievingExpression: UExpression) {
    val qualifiedServiceName = replacementMethod.getContainingUClass()?.qualifiedName ?: return
    val shortServiceName = StringUtil.getShortName(qualifiedServiceName)
    val message = DevKitBundle.message("inspection.simplifiable.service.retrieving.can.be.replaced.with", shortServiceName,
                                       replacementMethod.name)
    val methodNameProvider = MethodNameProviders.forLanguage(retrievingExpression.lang)
    val methodName = methodNameProvider?.getName(replacementMethod) ?: replacementMethod.name
    val fix = ReplaceWithGetInstanceCallFix(qualifiedServiceName, shortServiceName, methodName, howServiceRetrieved)
    when (retrievingExpression) {
      is UQualifiedReferenceExpression -> holder.registerUProblem(retrievingExpression, message, fixes = arrayOf(fix))
      is UCallExpression -> holder.registerUProblem(retrievingExpression, message, fixes = arrayOf(fix))
      else -> assert(false)
    }
  }

  private fun isGetInstanceProjectLevel(method: UMethod): Boolean {
    if (!(method.isStaticOrJvmStatic && method.visibility == UastVisibility.PUBLIC && method.uastParameters.size == 1)) {
      return false
    }
    val param = method.uastParameters[0]
    if (param.type.canonicalText != Project::class.java.canonicalName) return false
    val qualifiedRef = getReturnExpression(method)?.returnExpression as? UQualifiedReferenceExpression ?: return false
    return allGetServiceMethods.uCallMatches(qualifiedRef.selector as? UCallExpression) &&
           (qualifiedRef.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() == param
  }

  private fun isGetInstanceApplicationLevel(method: UMethod): Boolean {
    if (!(method.isStaticOrJvmStatic && method.visibility == UastVisibility.PUBLIC && method.uastParameters.isEmpty())) {
      return false
    }
    return when (val returnExpression = getReturnExpression(method)?.returnExpression) {
      is UQualifiedReferenceExpression -> {
        (componentManagerGetServiceMethods.uCallMatches(returnExpression.selector as? UCallExpression)
         && returnExpression.receiver.getExpressionType()?.isInheritorOf(Application::class.java.canonicalName) == true)
      }
      is UCallExpression -> serviceKtFileMethods.uCallMatches(returnExpression)
      else -> false
    }
  }

  private val UMethod.isStaticOrJvmStatic: Boolean
    get() = this.isStatic || this.findAnnotation(JvmStatic::class.java.canonicalName) != null

  private fun getReturnExpression(method: UMethod): UReturnExpression? {
    return (method.uastBody as? UBlockExpression)?.expressions?.singleOrNull() as? UReturnExpression
  }

  private class ReplaceWithGetInstanceCallFix(private val qualifiedServiceName: String,
                                              private val shortServiceName: String,
                                              private val methodName: String,
                                              private val howServiceRetrieved: Service.Level) : LocalQuickFix {

    override fun getFamilyName(): String {
      return DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", shortServiceName, methodName)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val call = descriptor.psiElement.toUElement()?.getParentOfType<UCallExpression>() ?: return
      val oldRetrievingExpression = getRetrievingExpression(call) ?: return
      val generationPlugin = UastCodeGenerationPlugin.byLanguage(descriptor.psiElement.language) ?: return
      val factory = generationPlugin.getElementFactory(project)
      val parameters = when {
        howServiceRetrieved == Service.Level.APP -> emptyList()
        howServiceRetrieved == Service.Level.PROJECT &&
        oldRetrievingExpression is UQualifiedReferenceExpression -> listOf(oldRetrievingExpression.receiver)
        else -> return
      }
      val context = oldRetrievingExpression.sourcePsi
      val receiver = factory.createQualifiedReference(qualifiedServiceName, context)
                     ?: factory.createSimpleReference(qualifiedServiceName, context)
      val newCall = factory.createCallExpression(receiver = receiver, methodName = methodName, parameters = parameters,
                                                 expectedReturnType = oldRetrievingExpression.getExpressionType(),
                                                 kind = UastCallKind.METHOD_CALL,
                                                 context = null) ?: return
      oldRetrievingExpression.replace(newCall)
    }
  }
}

private fun getRetrievingExpression(call: UCallExpression): UExpression? {
  return call.uastParent as? UQualifiedReferenceExpression ?: call as? UCallExpression
}