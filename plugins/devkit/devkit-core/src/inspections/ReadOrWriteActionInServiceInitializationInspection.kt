// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.idea.devkit.DevKitBundle
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

private val PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES = arrayOf(
  "loadState",
  "noStateLoaded",
  "initializeComponent"
)

private const val VISIT_CHILDREN = false
private const val SKIP_CHILDREN = true

internal class ReadOrWriteActionInServiceInitializationInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val actionType = node.getReadOrWriteActionRunMethodCallType()
          if (actionType.isReadOrWrite() && isCalledDuringServiceInitialization(node)) {
            registerProblem(node, actionType, holder)
          }
          return true
        }
      },
      arrayOf(UCallExpression::class.java)
    )
  }

  private fun UCallExpression.getReadOrWriteActionRunMethodCallType(): ActionType {
    return when {
      runReadActionMethods.uCallMatches(this) -> ActionType.READ
      runWriteActionMethods.uCallMatches(this) -> ActionType.WRITE
      else -> ActionType.NONE
    }
  }

  private fun isCalledDuringServiceInitialization(readOrWriteActionCall: UCallExpression): Boolean {
    val uClass = readOrWriteActionCall.getContainingNonCompanionObjectClass() ?: return false
    return isService(uClass) &&
           (isCalledDuringInit(readOrWriteActionCall) ||
            isInMethodCalledDuringInit(uClass, readOrWriteActionCall) ||
            isCalledDuringPersistentStateComponentInit(uClass, readOrWriteActionCall))
  }

  private fun UElement.getContainingNonCompanionObjectClass(): UClass? {
    val uClass = this.getContainingUClass() ?: return null
    return if (uClass.javaPsi.name == "Companion") uClass.getParentOfType<UClass>() else uClass
  }

  private fun isCalledDuringInit(readOrWriteActionCall: UCallExpression): Boolean {
    return !isCalledInAnonymousClassOrLambda(readOrWriteActionCall) &&
           (isCalledInConstructor(readOrWriteActionCall) ||
            isCalledInInitBlock(readOrWriteActionCall) ||
            isCalledInFieldAssignment(readOrWriteActionCall))
  }

  private fun isCalledInAnonymousClassOrLambda(readOrWriteActionCall: UCallExpression): Boolean {
    // do not report calls in listener, alarm, etc. registration
    return readOrWriteActionCall.getParentOfType<UObjectLiteralExpression>() != null ||
           readOrWriteActionCall.getParentOfType<ULambdaExpression>() != null
  }

  private fun isCalledInConstructor(readOrWriteActionCall: UCallExpression): Boolean {
    return readOrWriteActionCall.getContainingUMethod()?.isConstructor == true
  }

  private fun isCalledInInitBlock(readOrWriteActionCall: UCallExpression): Boolean {
    return readOrWriteActionCall.getParentOfType<UClassInitializer>() != null
  }

  private fun isCalledInFieldAssignment(readOrWriteActionCall: UCallExpression): Boolean {
    return readOrWriteActionCall.getParentOfType<UField>() != null
  }

  private fun isInMethodCalledDuringInit(serviceClass: UClass, readOrWriteActionCall: UCallExpression): Boolean {
    if (isCalledInAnonymousClassOrLambda(readOrWriteActionCall)) return false
    val companionObject = serviceClass.innerClasses.firstOrNull { it.javaPsi.name == "Companion" }
    val initializationElements: List<UElement> = serviceClass.getInitializationElements() +
                                                 (companionObject?.getInitializationElements() ?: emptyList())
    val containingMethod = readOrWriteActionCall.getContainingUMethod() ?: return false
    return containingMethod.isCalledInAnyOf(initializationElements)
  }

  private fun UClass.getInitializationElements(): List<UElement> {
    return this.methods.filter { it.isConstructor }.toList() +
           this.fields.asList() +
           this.initializers.asList()
  }

  private fun UMethod.isCalledInAnyOf(elements: List<UElement>): Boolean {
    val checkedMethod = this
    var isCalledDuringInitialization = false
    for (element in elements) {
      element.accept(object : UastVisitor {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (checkedMethod == node.resolveToUElement() && !isCalledInAnonymousClassOrLambda(node)) {
            isCalledDuringInitialization = true
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

  private fun isCalledDuringPersistentStateComponentInit(serviceClass: UClass, readOrWriteActionCall: UCallExpression): Boolean {
    return isPersistentStateComponent(serviceClass) &&
           (isCalledPersistentStateComponentInitMethods(readOrWriteActionCall) ||
            isInMethodCalledDuringPersistentStateComponentInit(serviceClass, readOrWriteActionCall))
  }

  private fun isPersistentStateComponent(serviceClass: UClass): Boolean {
    val servicePsiClass = serviceClass.javaPsi
    return JvmInheritanceUtil.isInheritor(servicePsiClass, PersistentStateComponent::class.java.canonicalName)
  }

  private fun isCalledPersistentStateComponentInitMethods(readOrWriteActionCall: UCallExpression): Boolean {
    if (isCalledInAnonymousClassOrLambda(readOrWriteActionCall)) return false
    val containingMethod = readOrWriteActionCall.getContainingUMethod() ?: return false
    return PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES.contains(containingMethod.name)
  }

  private fun isInMethodCalledDuringPersistentStateComponentInit(serviceClass: UClass, readOrWriteActionCall: UCallExpression): Boolean {
    if (isCalledInAnonymousClassOrLambda(readOrWriteActionCall)) return false
    val lifecycleMethods: List<UMethod> = serviceClass.methods.filter { PERSISTENT_STATE_COMPONENT_INIT_METHOD_NAMES.contains(it.name) }
    val containingMethod = readOrWriteActionCall.getContainingUMethod() ?: return false
    return containingMethod.isCalledInAnyOf(lifecycleMethods)
  }

  private fun registerProblem(uCallExpression: UCallExpression, actionType: ActionType, holder: ProblemsHolder) {
    val anchor = uCallExpression.methodIdentifier?.sourcePsi ?: return
    holder.registerProblem(
      anchor,
      if (actionType == ActionType.READ) DevKitBundle.message("inspection.read.or.write.action.during.service.init.message.read")
      else DevKitBundle.message("inspection.read.or.write.action.during.service.init.message.write")
    )
  }

  private enum class ActionType {
    READ, WRITE, NONE;

    fun isReadOrWrite() = this == READ || this == WRITE
  }

}
