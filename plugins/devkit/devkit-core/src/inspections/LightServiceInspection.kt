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
import com.intellij.psi.*
import com.intellij.psi.PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.callMatcher.CallMatcher
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LightServiceInspection : DevKitUastInspectionBase(UClass::class.java) {
  private companion object {
    private val SERVICE_FQN = Service::class.java.canonicalName
    private val SERVICE_LEVEL_FQN = Service.Level::class.java.canonicalName
    private val APPLICATION_FQN = Application::class.java.canonicalName
    private val PROJECT_FQN = Project::class.java.canonicalName
    private val COROUTINE_SCOPE_FQN = CoroutineScope::class.java.canonicalName
    private val COMPONENT_MANAGER_FQN = ComponentManager::class.java.canonicalName
    private val APP = Service.Level.APP.name
    private val PROJECT = Service.Level.PROJECT.name
    private const val GET_INSTANCE = "getInstance"
    private val COMPONENT_MANAGER_GET_SERVICE = CallMatcher.anyOf(
      CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
      CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getServiceIfCreated").parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
      CallMatcher.instanceCall(COMPONENT_MANAGER_FQN, "getService").parameterTypes(CommonClassNames.JAVA_LANG_CLASS, "boolean"))

    enum class Level { APP, PROJECT, APP_AND_PROJECT, NOT_SPECIFIED }

    fun getLevel(annotation: UAnnotation): Level {
      val levels = when (val value = annotation.findAttributeValue(DEFAULT_REFERENCED_METHOD_NAME)) {
        is UCallExpression -> value.valueArguments.mapNotNull { it.tryResolve() }.filterIsInstance<PsiField>().filter {
          it.containingClass?.qualifiedName == SERVICE_LEVEL_FQN && it.name in listOf(APP, PROJECT)
        }.map { it.name }

        is UReferenceExpression -> value.tryResolve()?.let { it as PsiField }?.takeIf { it.containingClass?.qualifiedName == SERVICE_LEVEL_FQN }?.name?.let {
          setOf(it)
        } ?: emptySet()

        else -> emptySet()
      }
      return when {
        levels.containsAll(setOf(APP, PROJECT)) -> Level.APP_AND_PROJECT
        levels.contains(APP) -> Level.APP
        levels.contains(PROJECT) -> Level.PROJECT
        else -> Level.NOT_SPECIFIED
      }
    }

    fun findGetProjectLevelInstance(uClass: UClass): UMethod? {
      return uClass.methods.find {
        val onlyParameter = ContainerUtil.getOnlyItem(it.uastParameters) ?: return@find false
        if (it.name == GET_INSTANCE && onlyParameter.type.canonicalText == PROJECT_FQN) {
          val qualifiedReference = getReturnExpression(it) ?: return@find false
          return@find COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedReference.selector as? UCallExpression) &&
                      (qualifiedReference.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() == onlyParameter
        }
        return@find false
      }
    }

    fun findGetApplicationLevelServiceInstance(uClass: UClass): UMethod? {
      return uClass.methods.find {
        if (it.name == GET_INSTANCE && it.uastParameters.isEmpty()) {
          val qualifiedReference = getReturnExpression(it) ?: return@find false
          if (COMPONENT_MANAGER_GET_SERVICE.uCallMatches(qualifiedReference.selector as? UCallExpression)) {
            return@find qualifiedReference.receiver.getExpressionType()?.isInheritorOf(APPLICATION_FQN) ?: false
          }
        }
        return@find false
      }
    }

    fun getReturnExpression(method: UMethod): UQualifiedReferenceExpression? {
      val body = method.uastBody ?: return null
      val onlyExpression = ContainerUtil.getOnlyItem((body as? UBlockExpression)?.expressions)
      val returnExpression = onlyExpression as? UReturnExpression ?: return null
      return returnExpression.returnExpression as? UQualifiedReferenceExpression
    }
  }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, object : AbstractUastNonRecursiveVisitor() {
    override fun visitClass(node: UClass): Boolean {
      val classToHighlight = node.getAnchorPsi() ?: return true
      val serviceAnnotation: UAnnotation = node.findAnnotation(SERVICE_FQN) ?: return true
      val annotationToHighlight = serviceAnnotation.sourcePsi ?: return true
      if (!node.isFinal) {
        val actions = createModifierActions(node, modifierRequest(JvmModifier.FINAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
        holder.registerProblem(classToHighlight, DevKitBundle.message("light.service.must.be.final"), *fixes)
      }

      val level = getLevel(serviceAnnotation)

      val constructors = node.methods.filter { it.isConstructor }
      if (!constructors.isEmpty()) {
        if (level == Level.APP) {
          val ctorWithProjectParam = constructors.find { it -> it.uastParameters.any { it.type.canonicalText == PROJECT_FQN } }
          val ctorToHighlight = ctorWithProjectParam.getAnchorPsi()
          if (ctorWithProjectParam != null && ctorToHighlight != null) {
            val actions = createAddAnnotationActions(node, annotationRequest(SERVICE_FQN, constantAttribute(DEFAULT_REFERENCED_METHOD_NAME,
                                                                                                            "com.intellij.openapi.components.Service.Level.PROJECT")))
            val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)

            holder.registerProblem(ctorToHighlight, DevKitBundle.message("project.level.required"), *fixes)
          }
        }
      }

      if (level == Level.APP || level == Level.APP_AND_PROJECT) {
        val hasNoArgOrCoroutineScopeCtor = constructors.any {
          it.uastParameters.isEmpty() ||
          ContainerUtil.getOnlyItem(it.uastParameters)?.type?.canonicalText == COROUTINE_SCOPE_FQN
        }

        if (!hasNoArgOrCoroutineScopeCtor) {
          val elementFactory = PsiElementFactory.getInstance(holder.project)
          val projectType = elementFactory.createTypeByFQClassName(COROUTINE_SCOPE_FQN, GlobalSearchScope.allScope(holder.project))
          val actions = createConstructorActions(node.javaPsi, constructorRequest(holder.project, emptyList())) +
                        createConstructorActions(node.javaPsi, constructorRequest(holder.project, listOf(Pair("scope", projectType))))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
          val message = DevKitBundle.message("application.level.service.requires.no.arg.or.coroutine.scope.ctor")
          holder.registerProblem(annotationToHighlight, message, *fixes)
        }
      }
      return true
    }

    override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
      val elementToHighlight = node.sourcePsi ?: return true
      if (!COMPONENT_MANAGER_GET_SERVICE.uCallMatches(node.selector as? UCallExpression)) return true
      val uClass = (node.selector.getExpressionType() as? PsiClassType)?.resolve()?.toUElement(UClass::class.java) ?: return true
      val serviceAnnotation: UAnnotation = uClass.findAnnotation(SERVICE_FQN) ?: return true
      val level = getLevel(serviceAnnotation)
      val receiverType = node.receiver.getExpressionType() ?: return true
      val containingMethod = node.getParentOfType<UMethod>()
      if (receiverType.isInheritorOf(PROJECT_FQN)) {
        if (level == Level.APP) {
          holder.registerProblem(elementToHighlight, DevKitBundle.message("project.level.service.retrieved.as.application.level"))
        }
        val getInstanceMethod = findGetProjectLevelInstance(uClass)
        if (getInstanceMethod != null && getInstanceMethod != containingMethod) {
          holder.registerProblem(elementToHighlight, DevKitBundle.message("can.be.replaced.with.get.instance"))
        }
      }
      else if (receiverType.isInheritorOf(APPLICATION_FQN)) {
        if (level == Level.PROJECT) {
          holder.registerProblem(elementToHighlight, DevKitBundle.message("application.level.service.retrieved.as.project.level"))
        }
        val getInstanceMethod = findGetApplicationLevelServiceInstance(uClass)
        if (getInstanceMethod != null && getInstanceMethod != containingMethod) {
          holder.registerProblem(elementToHighlight, DevKitBundle.message("can.be.replaced.with.get.instance"))
        }
      }
      return true
    }
  }, arrayOf(UClass::class.java, UQualifiedReferenceExpression::class.java))
}
