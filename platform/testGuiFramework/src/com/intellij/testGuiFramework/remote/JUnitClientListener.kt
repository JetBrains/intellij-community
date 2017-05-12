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

import org.junit.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * @author Sergey Karashevich
 */
class JUnitClientListener(val objectSender: ObjectSender) : RunListener() {

  override fun testStarted(description: Description?) {
    objectSender.send(JUnitInfo(Type.STARTED, description))
  }

  override fun testAssumptionFailure(failure: Failure?) {
    objectSender.send(JUnitInfo(Type.ASSUMPTION_FAILURE, failure.friendlySerializable()))
  }

  override fun testFailure(failure: Failure?) {
    objectSender.send(JUnitInfo(Type.FAILURE, failure))
  }

  override fun testFinished(description: Description?) {
    objectSender.send(JUnitInfo(Type.FINISHED, description))
  }

  override fun testIgnored(description: Description?) {
    objectSender.send(JUnitInfo(Type.IGNORED, description))
  }

  private fun Failure?.friendlySerializable(): Failure? {
    if (this == null) return null
    val e = this.exception as AssumptionViolatedException
    val newException = AssumptionViolatedException(e.toString(), e.cause)
    return Failure(this.description, newException)
  }
}