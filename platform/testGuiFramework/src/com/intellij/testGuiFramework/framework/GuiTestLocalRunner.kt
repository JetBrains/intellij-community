/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.launcher.ide.Ide
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.Suite
import org.junit.runners.model.FrameworkMethod


class GuiTestLocalRunner: BlockJUnit4ClassRunner, GuiTestRunnerInterface {

  override val ide: Ide?
  private val runner: GuiTestRunner
  override var mySuiteClass: Class<*>? = null

  constructor(testClass: Class<*>, suiteClass: Class<*>, ide: Ide?) : this(testClass, ide) {
    mySuiteClass = suiteClass
  }

  constructor(testClass: Class<*>, ideFromTest: Ide?) : super(testClass) {
    ide = ideFromTest
    runner = GuiTestRunner(this)
  }

  constructor(testClass: Class<*>): this(testClass, null)

  override fun getTestName(method: String): String {
    return method
  }

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    runner.runChild(method, notifier)
  }

  override fun doRunChild(method: FrameworkMethod, notifier: RunNotifier) {
    super.runChild(method, notifier)
  }

  override fun describeChild(method: FrameworkMethod): Description {
    return super.describeChild(method)
  }

  override fun getTestClassesNames(): List<String> {
    return if (mySuiteClass != null) {
      val annotation = mySuiteClass!!.getAnnotation(Suite.SuiteClasses::class.java)
      if (annotation?.value !is Array<*>) throw Exception(
        "Annotation @Suite.SuiteClasses for suite doesn't contain classes as value or is null")
      val array = annotation.value
      array.map {
        it.java.canonicalName
      }
    }
    else {
      listOf(this.testClass.javaClass.canonicalName)
    }
  }
}