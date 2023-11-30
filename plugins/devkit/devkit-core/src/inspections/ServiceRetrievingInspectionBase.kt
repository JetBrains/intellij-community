// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement

internal abstract class ServiceRetrievingInspectionBase(
  additionalComponentManagerMethodNames: Array<String> = emptyArray(),
  additionalServiceKtFileMethodNames: Array<String> = emptyArray()
) : DevKitUastInspectionBase() {

  protected data class ServiceRetrievingInfo(val howServiceRetrieved: Service.Level,
                                             val serviceClass: UClass)

  protected val serviceKtFileMethods = CallMatcher.staticCall(
    "com.intellij.openapi.components.ServiceKt", "service", *additionalServiceKtFileMethodNames
  ).parameterCount(0)

  protected val componentManagerGetServiceMethods = CallMatcher.anyOf(
    CallMatcher.instanceCall(ComponentManager::class.java.canonicalName, "getService", *additionalComponentManagerMethodNames)
      .parameterTypes(CommonClassNames.JAVA_LANG_CLASS),
    CallMatcher.staticCall("com.intellij.openapi.components.ServicesKt", "service", *additionalServiceKtFileMethodNames)
      .parameterTypes(ComponentManager::class.java.canonicalName),
  )

  protected val allGetServiceMethods = CallMatcher.anyOf(componentManagerGetServiceMethods, serviceKtFileMethods)

  protected fun getServiceRetrievingInfo(node: UCallExpression): ServiceRetrievingInfo? {
    if (!allGetServiceMethods.uCallMatches(node)) return null
    val howServiceRetrieved = howServiceRetrieved(node) ?: return null
    val serviceType = node.returnType as? PsiClassType ?: return null
    val serviceClass = serviceType.resolve()?.toUElement(UClass::class.java) ?: return null
    return ServiceRetrievingInfo(howServiceRetrieved, serviceClass)
  }

  protected fun howServiceRetrieved(getServiceCandidate: UCallExpression): Service.Level? {
    if (serviceKtFileMethods.uCallMatches(getServiceCandidate)) return Service.Level.APP
    val receiverType = getServiceCandidate.receiver?.getExpressionType() ?: return null
    val aClass = (receiverType as? PsiClassType)?.resolve() ?: return null
    return when {
      InheritanceUtil.isInheritor(aClass, Application::class.java.canonicalName) -> Service.Level.APP
      InheritanceUtil.isInheritor(aClass, Project::class.java.canonicalName) -> Service.Level.PROJECT
      else -> null
    }
  }
}
