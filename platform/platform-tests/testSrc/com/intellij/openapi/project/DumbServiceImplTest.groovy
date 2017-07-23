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
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
/**
 * @author peter
 */
class DumbServiceImplTest extends LightPlatformCodeInsightFixtureTestCase {

  void "test runWhenSmart is executed synchronously in smart mode"() {
    int invocations = 0
    dumbService.runWhenSmart { invocations++ }
    assert invocations == 1
  }

  void "test runWhenSmart is executed on EDT without write action"() {
    ApplicationManager.application.assertIsDispatchThread()
    int invocations = 0

    Semaphore semaphore = new Semaphore(1)
    dumbService.queueAsynchronousTask(new DumbModeTask() {
      @Override
      void performInDumbMode(@NotNull ProgressIndicator indicator) {
        assert !ApplicationManager.application.dispatchThread
        edt {
          dumbService.runWhenSmart {
            invocations++
            ApplicationManager.application.assertIsDispatchThread()
            assert !ApplicationManager.application.writeAccessAllowed
          }
        }
        TimeoutUtil.sleep(100)
        semaphore.up()
      }
    })
    assert dumbService.dumb
    assert invocations == 0
    UIUtil.dispatchAllInvocationEvents()
    assert semaphore.waitFor(1000)
    UIUtil.dispatchAllInvocationEvents()
    assert invocations == 1
  }

  private DumbServiceImpl getDumbService() {
    (DumbServiceImpl)DumbService.getInstance(project)
  }
}
