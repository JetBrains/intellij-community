/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.fail

class RunnableActionsTest : BareTestFixtureTestCase() {
  private val message = "<test message>"

  @Test fun readAction() = doTest(object : ReadAction<Any>() {
    override fun run(result: Result<Any>) = throw Exception(message)
  })

  @Test fun writeAction() = doTest(object : WriteAction<Any>() {
    override fun run(result: Result<Any>) = throw Exception(message)
  })

  @Test fun commandAction() = doTest(object : WriteCommandAction<Any>(null) {
    override fun run(result: Result<Any>) = throw Exception(message)
  })

  private fun doTest(action: BaseActionRunnable<Any>) {
    try {
      action.execute()
      fail("BaseActionRunnable.execute() should pass exceptions")
    }
    catch (e: RuntimeException) {
      assertThat(e.message).endsWith(message)
    }

    val result = try {
      action.executeSilently()
    }
    catch(e: RuntimeException) {
      fail("BaseActionRunnable.executeSilently() should capture exceptions")
    }
    assertThat(result.hasException()).isTrue()
    assertThat(result.throwable.message).endsWith(message)
  }
}