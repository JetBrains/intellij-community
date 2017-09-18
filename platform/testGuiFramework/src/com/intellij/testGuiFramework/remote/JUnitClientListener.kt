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
package com.intellij.testGuiFramework.remote

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.JUnitInfo
import com.intellij.testGuiFramework.remote.transport.Type
import org.junit.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * @author Sergey Karashevich
 */
class JUnitClientListener(val sendObjectFun: (JUnitInfo) -> Unit) : RunListener() {

  val LOG = Logger.getInstance("#com.intellij.testGuiFramework.remote.JUnitClientListener")

  override fun testStarted(description: Description?) {
    description ?: throw Exception("Unable to send notification to JUnitServer that test is starter due to null description!")
    //don't send start state to server if it is a resumed test
    if (GuiTestOptions.getResumeTestName() == JUnitInfo.getClassAndMethodName(description))
    sendObjectFun(JUnitInfo(Type.STARTED, description, JUnitInfo.getClassAndMethodName(description)))
  }

  override fun testAssumptionFailure(failure: Failure?) {
    sendObjectFun(JUnitInfo(Type.ASSUMPTION_FAILURE, failure.friendlySerializable(), JUnitInfo.getClassAndMethodName(failure!!.description)))
  }

  override fun testFailure(failure: Failure?) {
    sendObjectFun(JUnitInfo(Type.FAILURE, failure!!.exception, JUnitInfo.getClassAndMethodName(failure.description)))
    LOG.error(failure.exception)
  }

  override fun testFinished(description: Description?) {
    sendObjectFun(JUnitInfo(Type.FINISHED, description, JUnitInfo.getClassAndMethodName(description!!)))
  }

  override fun testIgnored(description: Description?) {
    sendObjectFun(JUnitInfo(Type.IGNORED, description, JUnitInfo.getClassAndMethodName(description!!)))
  }


  private fun Failure?.friendlySerializable(): Failure? {
    if (this == null) return null
    val e = this.exception as AssumptionViolatedException
    val newException = AssumptionViolatedException(e.toString(), e.cause)
    return Failure(this.description, newException)
  }

}