// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.UastVisitor

private val runReadActionMethods: CallMatcher = CallMatcher.anyOf(
  CallMatcher.instanceCall("com.intellij.openapi.application.Application", "runReadAction", "runWriteIntentReadAction"),
  CallMatcher.staticCall("com.intellij.openapi.application.ActionsKt", "runReadAction"),
  CallMatcher.staticCall("com.intellij.openapi.application.ReadAction", "compute", "computeCancellable", "run"),
  CallMatcher.instanceCall("com.intellij.openapi.application.ReadAction", "execute"),
  CallMatcher.instanceCall("com.intellij.openapi.application.NonBlockingReadAction", "executeSynchronously"), // no submit
)

private val runWriteActionMethods: CallMatcher = CallMatcher.anyOf(
  CallMatcher.instanceCall("com.intellij.openapi.application.Application", "runWriteAction"),
  CallMatcher.staticCall("com.intellij.openapi.application.ActionsKt", "runWriteAction", "runUndoTransparentWriteAction"),
  CallMatcher.staticCall("com.intellij.openapi.application.WriteAction", "compute", "computeAndWait", "start", "run", "runAndWait"),
  CallMatcher.instanceCall("com.intellij.openapi.application.WriteAction", "execute"),
)

private val invokeAndWaitMethods: CallMatcher = CallMatcher.anyOf(
  CallMatcher.instanceCall("com.intellij.openapi.application.Application", "invokeAndWait"),
  CallMatcher.instanceCall("com.intellij.util.ui.EdtInvocationManager", "invokeAndWait"),
  CallMatcher.instanceCall("com.intellij.openapi.application.impl.LaterInvocator", "invokeAndWait"),
)

private val PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES = arrayOf(
  "loadState",
  "noStateLoaded",
  "initializeComponent"
)

private const val VISIT_CHILDREN = false
private const val SKIP_CHILDREN = true

internal class PotentialDeadlockInServiceInitializationInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val forbiddenCallType = node.getForbiddenMethodCallType() ?: return true
          val callContextHolder = CallContextHolder()
          if (isCalledDuringServiceInitialization(node, callContextHolder)) {
            registerProblem(node, forbiddenCallType, holder, callContextHolder)
          }
          return true
        }
      },
      arrayOf(UCallExpression::class.java)
    )
  }

  private fun UCallExpression.getForbiddenMethodCallType(): CallType? {
    return when {
      runReadActionMethods.uCallMatches(this) -> CallType.READ
      runWriteActionMethods.uCallMatches(this) -> CallType.WRITE
      invokeAndWaitMethods.uCallMatches(this) -> CallType.INVOKE_AND_WAIT
      else -> null
    }
  }

  private fun isCalledDuringServiceInitialization(forbiddenCall: UCallExpression, callContextHolder: CallContextHolder): Boolean {
    val uClass = forbiddenCall.getContainingNonCompanionObjectClass() ?: return false
    return isService(uClass) &&
           (isCalledDuringInit(forbiddenCall) ||
            isInMethodCalledDuringInit(uClass, forbiddenCall, callContextHolder) ||
            isCalledDuringPersistentStateComponentInit(uClass, forbiddenCall, callContextHolder))
  }

  private fun UElement.getContainingNonCompanionObjectClass(): UClass? {
    val uClass = this.getContainingUClass() ?: return null
    return if (uClass.javaPsi.name == "Companion") uClass.getParentOfType<UClass>() else uClass
  }

  private fun isCalledDuringInit(forbiddenCall: UCallExpression): Boolean {
    return !isCalledInAnonymousClassOrLambda(forbiddenCall) &&
           (isCalledInConstructor(forbiddenCall) ||
            isCalledInInitBlock(forbiddenCall) ||
            isCalledInFieldAssignment(forbiddenCall))
  }

  private fun isCalledInAnonymousClassOrLambda(forbiddenActionCall: UCallExpression): Boolean {
    // do not report calls in listener, alarm, etc. registration
    return forbiddenActionCall.getParentOfType<UObjectLiteralExpression>() != null ||
           forbiddenActionCall.getParentOfType<ULambdaExpression>() != null
  }

  private fun isCalledInConstructor(forbiddenCall: UCallExpression): Boolean {
    return forbiddenCall.getContainingUMethod()?.isConstructor == true
  }

  private fun isCalledInInitBlock(forbiddenCall: UCallExpression): Boolean {
    return forbiddenCall.getParentOfType<UClassInitializer>() != null
  }

  private fun isCalledInFieldAssignment(forbiddenCall: UCallExpression): Boolean {
    return forbiddenCall.getParentOfType<UField>() != null
  }

  private fun isInMethodCalledDuringInit(serviceClass: UClass, forbiddenCall: UCallExpression, callContextHolder: CallContextHolder): Boolean {
    if (isCalledInAnonymousClassOrLambda(forbiddenCall)) return false
    val companionObject = serviceClass.innerClasses.firstOrNull { it.javaPsi.name == "Companion" }
    val initializationElements: List<UElement> = serviceClass.getInitializationElements() +
                                                 (companionObject?.getInitializationElements() ?: emptyList())
    val containingMethod = forbiddenCall.getContainingUMethod() ?: return false
    return containingMethod.isCalledInAnyOf(initializationElements, callContextHolder)
  }

  private fun UClass.getInitializationElements(): List<UElement> {
    return this.methods.filter { it.isConstructor }.toList() +
           this.fields.asList() +
           this.initializers.asList()
  }

  private fun UMethod.isCalledInAnyOf(potentialCallers: List<UElement>, callContextHolder: CallContextHolder): Boolean {
    val checkedMethod = this
    var isCalledDuringInitialization = false
    for (potentialCaller in potentialCallers) {
      potentialCaller.accept(object : UastVisitor {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (checkedMethod == node.resolveToUElement() && !isCalledInAnonymousClassOrLambda(node)) {
            isCalledDuringInitialization = true
            callContextHolder.value = getContextText(checkedMethod, potentialCaller)
            return SKIP_CHILDREN
          }
          return VISIT_CHILDREN
        }

        override fun visitElement(node: UElement) = VISIT_CHILDREN
      })
      if (isCalledDuringInitialization) {
        break
      }
    }
    return isCalledDuringInitialization
  }

  private fun getContextText(calledMethod: UMethod, caller: UElement): @Nls String? {
    val callerName = when (caller) {
      is UMethod ->
        if (caller.isConstructor) message("inspection.potential.deadlock.during.service.init.message.context.constructor", caller.name)
        else message("inspection.potential.deadlock.during.service.init.message.context.method", caller.name)

      is UField ->
        message("inspection.potential.deadlock.during.service.init.message.context.field", @Suppress("UElementAsPsi") caller.name)

      is UClassInitializer ->
        // there is no API in UAST to distinguish companion object context, but we can live with it
        if (caller.isStatic) message("inspection.potential.deadlock.during.service.init.message.context.static.initializer")
        else message("inspection.potential.deadlock.during.service.init.message.context.instance.initializer")

      else -> return null
    }
    return message("inspection.potential.deadlock.during.service.init.message.context", "'${calledMethod.name}'", callerName)
  }

  private fun isCalledDuringPersistentStateComponentInit(
    serviceClass: UClass,
    forbiddenCall: UCallExpression,
    callContextHolder: CallContextHolder,
  ): Boolean {
    return isPersistentStateComponent(serviceClass) &&
           (isCalledDuringPersistentStateComponentInitMethods(forbiddenCall) ||
            isInMethodCalledDuringPersistentStateComponentInit(serviceClass, forbiddenCall, callContextHolder))
  }

  private fun isPersistentStateComponent(serviceClass: UClass): Boolean {
    val servicePsiClass = serviceClass.javaPsi
    return JvmInheritanceUtil.isInheritor(servicePsiClass, PersistentStateComponent::class.java.canonicalName)
  }

  private fun isCalledDuringPersistentStateComponentInitMethods(forbiddenCall: UCallExpression): Boolean {
    if (isCalledInAnonymousClassOrLambda(forbiddenCall)) return false
    val containingMethod = forbiddenCall.getContainingUMethod() ?: return false
    return PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES.contains(containingMethod.name)
  }

  private fun isInMethodCalledDuringPersistentStateComponentInit(
    serviceClass: UClass, forbiddenCall: UCallExpression,
    callContextHolder: CallContextHolder,
  ): Boolean {
    if (isCalledInAnonymousClassOrLambda(forbiddenCall)) return false
    val lifecycleMethods: List<UMethod> = serviceClass.methods.filter { PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES.contains(it.name) }
    val containingMethod = forbiddenCall.getContainingUMethod() ?: return false
    return containingMethod.isCalledInAnyOf(lifecycleMethods, callContextHolder)
  }

  private fun registerProblem(
    uCallExpression: UCallExpression,
    actionType: CallType,
    holder: ProblemsHolder,
    callContextHolder: CallContextHolder,
  ) {
    val anchor = uCallExpression.methodIdentifier?.sourcePsi ?: return
    val callContext = callContextHolder.value ?: ""
    val message = when (actionType) {
      CallType.READ -> message("inspection.potential.deadlock.during.service.init.message.read", callContext)
      CallType.WRITE -> message("inspection.potential.deadlock.during.service.init.message.write", callContext)
      CallType.INVOKE_AND_WAIT -> message("inspection.potential.deadlock.during.service.init.message.invoke.and.wait", callContext)
    }
    holder.registerProblem(anchor, message)
  }

  private enum class CallType {
    READ, WRITE, INVOKE_AND_WAIT;
  }

  private class CallContextHolder(@Nls var value: String? = null)

}
