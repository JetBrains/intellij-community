// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.isInheritorOf
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.callMatcher.CallMatcher
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.ReplaceWithGetInstanceCallFix
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LightServiceInspection : DevKitUastInspectionBase(UClass::class.java) {
  private val COMPONENT_MANAGER_FQN = ComponentManager::class.java.canonicalName
  private val COMPONENT_MANAGER_GET_SERVICE = CallMatcher.anyOf(
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getServiceIfCreated").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS, "boolean"))

  enum class Level { APP, PROJECT, APP_AND_PROJECT, NOT_SPECIFIED }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, object : AbstractUastNonRecursiveVisitor() {
    override fun visitClass(node: UClass): Boolean {
      val classToHighlight = node.getAnchorPsi() ?: return true
      val serviceAnnotation: UAnnotation = node.findAnnotation(Service::class.java.canonicalName) ?: return true
      val annotationToHighlight = serviceAnnotation.sourcePsi ?: return true
      if (!node.isFinal) {
        val actions = createModifierActions(node, modifierRequest(JvmModifier.FINAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
        holder.registerProblem(classToHighlight, DevKitBundle.message("inspection.light.service.must.be.final"), *fixes)
      }
      val level = getLevel(serviceAnnotation)
      val constructors = node.methods.filter { it.isConstructor }
      if (constructors.isEmpty()) return true
      if (level !in listOf(Level.PROJECT, Level.APP_AND_PROJECT)) {
        val ctorWithProjectParam = constructors.find {
          ContainerUtil.getOnlyItem(it.uastParameters)?.type?.canonicalText == Project::class.java.canonicalName
        }
        val ctorToHighlight = ctorWithProjectParam.getAnchorPsi() ?: return true
        if (ctorWithProjectParam != null) {
          val request = annotationRequest(Service::class.java.canonicalName, constantAttribute(DEFAULT_REFERENCED_METHOD_NAME,
                                                                                               "com.intellij.openapi.components.Service.Level.PROJECT"))
          val actions = createAddAnnotationActions(node.javaPsi, request)
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)

          holder.registerProblem(ctorToHighlight, DevKitBundle.message("inspection.light.service.must.be.project.level"), *fixes)
        }
      }

      if (level == Level.APP || level == Level.APP_AND_PROJECT) {
        val hasNoArgOrCoroutineScopeCtor = constructors.any {
          it.uastParameters.isEmpty() || ContainerUtil.getOnlyItem(
            it.uastParameters)?.type?.canonicalText == CoroutineScope::class.java.canonicalName
        }

        if (!hasNoArgOrCoroutineScopeCtor) {
          val elementFactory = PsiElementFactory.getInstance(holder.project)
          val projectType = elementFactory.createTypeByFQClassName(CoroutineScope::class.java.canonicalName,
                                                                   GlobalSearchScope.allScope(holder.project))
          val actions = createConstructorActions(node.javaPsi, constructorRequest(holder.project, emptyList())) +
                        createConstructorActions(node.javaPsi, constructorRequest(holder.project, listOf(Pair("scope", projectType))))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
          val message = DevKitBundle.message("inspection.light.service.must.have.no.arg.constructor")
          holder.registerProblem(annotationToHighlight, message, *fixes)
        }
      }
      return true
    }

    override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
      val toHighlight = node.sourcePsi ?: return true
      if (!COMPONENT_MANAGER_GET_SERVICE.uCallMatches(node.selector as? UCallExpression)) return true
      val uClass = (node.selector.getExpressionType() as? PsiClassType)?.resolve()?.toUElement(UClass::class.java) ?: return true
      if (isInsideGetInstance(node, uClass)) return true
      val serviceAnnotation = uClass.findAnnotation(Service::class.java.canonicalName) ?: return true
      val level = getLevel(serviceAnnotation)
      val receiverType = node.receiver.getExpressionType() ?: return true
      val array = listOf(
        MismatchReceivingChecker(Project::class.java.canonicalName, Level.APP,
                                 "inspection.light.service.application.level.retrieved.as.project.level"),
        MismatchReceivingChecker(Application::class.java.canonicalName, Level.PROJECT,
                                 "inspection.light.service.project.level.retrieved.as.application.level"))
      val hasError = array.any { it.check(level, receiverType, toHighlight, holder) }
      if (!hasError) checkIfCanBeReplacedWithGetInstance(uClass, receiverType, holder, toHighlight)
      return true
    }
  }, arrayOf(UClass::class.java, UQualifiedReferenceExpression::class.java))

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
    val message = DevKitBundle.message("inspection.light.service.can.be.replaced.with", serviceName, method.name)
    holder.registerProblem(toHighlight, message, ReplaceWithGetInstanceCallFix(serviceName, method.name, isApplicationLevelService))
  }

  private fun isInsideGetInstance(node: UExpression, uClass: UClass): Boolean {
    val returnExpr = node.uastParent as? UReturnExpression ?: return false
    val method = returnExpr.jumpTarget as? UMethod ?: return false
    val returnExpression = getReturnExpression(method)
    return method.returnTypeReference?.getQualifiedName() == uClass.qualifiedName &&
           returnExpression?.sourcePsi === returnExpr.sourcePsi
  }

  private fun getLevel(annotation: UAnnotation): Level {
    val levels = when (val value = annotation.findAttributeValue(DEFAULT_REFERENCED_METHOD_NAME)) {
      is UCallExpression -> value
        .valueArguments
        .mapNotNull { it.tryResolve() }
        .filterIsInstance<PsiField>()
        .filter {
          it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName &&
          it.name in listOf(Service.Level.APP.name, Service.Level.PROJECT.name)
        }
        .map { it.name }

      is UReferenceExpression -> value
                                   .tryResolve()
                                   ?.let { it as PsiField }
                                   ?.takeIf { it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName }
                                   ?.name
                                   ?.let { setOf(it) } ?: emptySet()

      else -> emptySet()
    }
    return when {
      levels.containsAll(setOf(Service.Level.APP.name, Service.Level.PROJECT.name)) -> Level.APP_AND_PROJECT
      levels.contains(Service.Level.APP.name) -> Level.APP
      levels.contains(Service.Level.PROJECT.name) -> Level.PROJECT
      else -> Level.NOT_SPECIFIED
    }
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
    return ContainerUtil.getOnlyItem((method.uastBody as? UBlockExpression)?.expressions) as? UReturnExpression
  }
}
