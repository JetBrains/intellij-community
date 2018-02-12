// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import kotlin.reflect.KClass


internal interface GuiTestRunnerInterface {
  val ide: Ide?
  var mySuiteClass: Class<*>?

  fun describeChild(method: FrameworkMethod): Description
  fun doRunChild(method: FrameworkMethod, notifier: RunNotifier)
  fun getTestName(method: String): String
  fun getTestClassesNames(): List<String>
}


fun getIdeFromAnnotation(testClass: Class<*>): Ide {
  val annotation = testClass.annotations.filterIsInstance<RunWithIde>().firstOrNull()?.value
  val ideType = if (annotation != null) (annotation as KClass<out IdeType>).java.newInstance() else CommunityIde()
  return Ide(ideType, 0, 0)
}