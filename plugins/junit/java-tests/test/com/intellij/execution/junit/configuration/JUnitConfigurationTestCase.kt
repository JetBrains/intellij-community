// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer
import com.intellij.java.execution.BaseConfigurationTestCase
import com.intellij.psi.PsiElement
import com.intellij.testFramework.MapDataContext

abstract class JUnitConfigurationTestCase : BaseConfigurationTestCase() {
  protected open fun createJUnitConfiguration(
    psiElement: PsiElement,
    producerClass: Class<out AbstractJavaTestConfigurationProducer<*>>,
    dataContext: MapDataContext,
  ): JUnitConfiguration? {
    val context = createContext(psiElement, dataContext)
    val producer: RunConfigurationProducer<*> = RunConfigurationProducer.getInstance(producerClass)
    val fromContext = producer.createConfigurationFromContext(context)
    assertNotNull(fromContext)
    return fromContext!!.configuration as JUnitConfiguration
  }

  protected fun checkPackage(packageName: String?, configuration: JUnitConfiguration) {
    assertEquals(packageName, configuration.persistentData.packageName)
  }

  protected fun checkClassName(className: String?, configuration: JUnitConfiguration) {
    assertEquals(className, configuration.persistentData.mainClassName)
  }

  protected fun checkMethodName(methodName: String?, configuration: JUnitConfiguration) {
    assertEquals(methodName, configuration.persistentData.getMethodName())
  }

  protected fun checkTestObject(testObjectKey: String?, configuration: JUnitConfiguration) {
    assertEquals(testObjectKey, configuration.persistentData.TEST_OBJECT)
  }
}