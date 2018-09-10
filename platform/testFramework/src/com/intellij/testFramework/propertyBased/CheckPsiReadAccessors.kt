// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser.psiTraverser
import org.jetbrains.jetCheck.ImperativeCommand.Environment
import java.beans.Introspector
import java.lang.reflect.Method

/**
 * Checks that all read accessors on PSI elements in the file don't throw exceptions.
 */
class CheckPsiReadAccessors(file: PsiFile, private val skipCondition: Condition<in Method>) : ActionOnFile(file) {

  override fun performCommand(env: Environment) {
    for (element in psiTraverser(file)) {
      invokeAllReadAccessors(element)
    }
  }

  private fun invokeAllReadAccessors(instance: Any) {
    val descriptors = Introspector.getBeanInfo(instance.javaClass).propertyDescriptors ?: return
    for (descriptor in descriptors) {
      val method = descriptor.readMethod ?: continue
      if (skipCondition.value(method)) continue
      try {
        method(instance)
      }
      catch (e: Throwable) {
        throw RuntimeException("${method.declaringClass}#${method.name}", e)
      }
    }
  }
}
