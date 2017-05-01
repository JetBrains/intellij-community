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

import com.intellij.openapi.diagnostic.Logger
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError

class GuiTestRunner @Throws(InitializationError::class)
constructor(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    notifier.addListener(object: RunListener() {
      override fun testFailure(failure: Failure?) {
        LOG.error("Test failed: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testFailure(failure)
      }

      override fun testFinished(description: Description?) {
        LOG.info("Test finished: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testFinished(description)
      }

      override fun testIgnored(description: Description?) {
        LOG.info("Test ignored: '${testClass.name}.${method.name}'")
        notifier.removeListener(this)
        super.testIgnored(description)
      }
    })
    LOG.info("Starting test: '${testClass.name}.${method.name}'")
    if (GuiTestUtil.doesIdeHaveFatalErrors()) {
      notifier.fireTestIgnored(describeChild(method))
      LOG.error("Skipping test '${method.name}': a fatal error has occurred in the IDE" )
      notifier.pleaseStop()
    }
    else {
      super.runChild(method, notifier)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GuiTestRunner::class.java)
  }


}
