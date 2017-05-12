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
package com.intellij.testGuiFramework.remote.server

import com.intellij.testGuiFramework.remote.JUnitInfo
import com.intellij.testGuiFramework.remote.Type
import org.junit.Assert
import org.junit.internal.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import java.util.concurrent.CountDownLatch

/**
 * @author Sergey Karashevich
 */
class RemoteTestRunner(testClass: Class<*>): BlockJUnit4ClassRunner(testClass) {

    override fun runChild(method: FrameworkMethod?, notifier: RunNotifier?) {

        val description = this@RemoteTestRunner.describeChild(method)
        val eachNotifier = EachTestNotifier(notifier, description)

        val cdl = CountDownLatch(1)

        val myServerHandler = object : ServerHandler() {

            override fun acceptObject(obj: Any) = obj is JUnitInfo
            override fun handleObject(obj: Any) {
                assert(acceptObject(obj))
                val jUnitInfo = obj as JUnitInfo
                when(jUnitInfo.type) {
                    Type.STARTED -> eachNotifier.fireTestStarted()
                    Type.ASSUMPTION_FAILURE -> eachNotifier.addFailedAssumption((jUnitInfo.obj as Failure).exception as AssumptionViolatedException)
                    Type.IGNORED -> { notifier!!.fireTestIgnored(description) }
                    Type.FAILURE -> eachNotifier.addFailure((jUnitInfo.obj as Failure).exception)
                    Type.FINISHED -> { eachNotifier.fireTestFinished(); cdl.countDown()}
                    else -> throw UnsupportedOperationException("Unable to recognize received from JUnitClient")
                }
            }
        }

        try {
            JUnitServer.getServer().registerServerHandler(myServerHandler)
            JUnitServer.getServer().setServerFailsHandler(runnableThrowable = { throwable -> cdl.countDown(); throw throwable})
            methodBlock(method).evaluate()
        } catch (e: Exception) {
            notifier!!.fireTestIgnored(description)
            Assert.fail(e.message)
            cdl.countDown()
        }
        cdl.await()
        JUnitServer.Companion.getServer().unregisterServerHandler(myServerHandler)
    }

}