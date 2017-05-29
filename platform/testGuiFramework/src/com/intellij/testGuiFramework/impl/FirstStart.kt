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
package com.intellij.testGuiFramework.impl

import com.intellij.ide.PrivacyPolicy
import com.intellij.idea.Main
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.util.PlatformUtils
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Frame
import java.util.concurrent.TimeUnit


/**
 * could be used only to initialize IDEA configuration for the first start. Please do not start it with GuiTestCase runner.
 *
 */

fun main(args: Array<String>) {
  guessIdeAndStartRobot()
  Main.main(args)
}

fun guessIdeAndStartRobot(): FirstStart {
  when (System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.IDEA_PREFIX)) {
    PlatformUtils.IDEA_PREFIX -> return IdeaUltimateFirstStart()
    PlatformUtils.IDEA_CE_PREFIX -> return IdeaCommunityFirstStart()
    PlatformUtils.APPCODE_PREFIX -> TODO("Create complete installation case for AppCode")
    PlatformUtils.CLION_PREFIX -> TODO("Create complete installation case for CLion")
    PlatformUtils.PYCHARM_PREFIX -> TODO("Create complete installation case for PyCharm")
    PlatformUtils.PYCHARM_CE_PREFIX -> TODO("Create complete installation case for PyCharm")
    PlatformUtils.PYCHARM_EDU_PREFIX -> TODO("Create complete installation case for PyCharm")
    PlatformUtils.RUBY_PREFIX -> TODO("Create complete installation case for RubyMine")
    PlatformUtils.PHP_PREFIX -> TODO("Create complete installation case for PhpStorm")
    PlatformUtils.WEB_PREFIX -> return WebStormFirstStart()
    PlatformUtils.DBE_PREFIX -> TODO("Create complete installation case for DataGrip")
    PlatformUtils.RIDER_PREFIX -> TODO("Create complete installation case for Rider")
    PlatformUtils.GOIDE_PREFIX -> TODO("Create complete installation case for Gogland")
    else -> return IdeaCommunityFirstStart()
  }
}

abstract class FirstStart(val ideType: IdeType) : GuiTestCase() {

  private val FIRST_START_ROBOT_THREAD = "First Start Robot Thread"

  val robotThread: Thread = object : Thread(FIRST_START_ROBOT_THREAD) {
    override fun run() {
      while (awtIsNotStarted()) Thread.sleep(100)
      completeFirstStart()
    }
  }

  init {
    myRobot = SmartWaitRobot()
    robotThread.start()
  }

  protected abstract fun completeFirstStart()


  protected fun reflectiveExit() {
    println("performing reflective exit")
    val classLoader = Frame.getFrames().find { it.javaClass.name.contains("FlatWelcomeFrame") }?.javaClass?.classLoader
    val appManager = classLoader!!.loadClass(ApplicationManager::class.java.name)
    val getAppMethod = appManager.getMethod("getApplication")
    val app = getAppMethod.invoke(null)
    app.javaClass.getMethod("exit").invoke(app)
  }

  private fun awtIsNotStarted() = !(Thread.getAllStackTraces().keys.any { thread -> thread.name.toLowerCase().contains("awt") })

  protected fun waitWelcomeFrame() {
    val checkIsFrame: (Frame) -> Boolean = {frame -> frame.javaClass.simpleName == "FlatWelcomeFrame"}
    Pause.pause(object : Condition("Welcome Frame to show up") {
      override fun test() = Frame.getFrames().any { checkIsFrame(it) }
    }, GuiTestUtil.LONG_TIMEOUT)
  }

  protected fun acceptAgreement() {
    val policyAgreementTitle = "Privacy Policy Agreement"
    val policy = PrivacyPolicy.getContent()
    val showPrivacyPolicyAgreement = !PrivacyPolicy.isVersionAccepted(policy.getFirst())
    if (!showPrivacyPolicyAgreement) return
    try {
      with(JDialogFixture.findByPartOfTitle(myRobot, policyAgreementTitle, Timeout.timeout(2, TimeUnit.MINUTES))) {
        button("Accept").click()
      }
    } catch (e: WaitTimedOutError) {
      //TODO: add logger here
    }
  }

  protected fun closeAll() {
//    myRobot.cleanUp()
    reflectiveExit()
  }

}

class IdeaCommunityFirstStart : FirstStart(ideType = IdeType.IDEA_COMMUNITY) {

  override fun completeFirstStart() {
    acceptAgreement()
    dialog("Complete Installation") {
      radioButton("Do not import settings").select()
      button("OK").click()
    }
    dialog("Customize IntelliJ IDEA") {
      button("Skip All and Set Defaults").click()
    }
    waitWelcomeFrame()
    closeAll()
  }
}

class IdeaUltimateFirstStart : FirstStart(ideType = IdeType.IDEA_ULTIMATE) {

  override fun completeFirstStart() {
    acceptAgreement()
    dialog("Complete Installation") {
      radioButton("Do not import settings").select()
      button("OK").click()
    }
    dialog("Customize IntelliJ IDEA") {
      button("Skip All and Set Defaults").click()
    }
    waitWelcomeFrame()
    closeAll()
  }
}

class WebStormFirstStart : FirstStart(ideType = IdeType.WEBSTORM) {

  override fun completeFirstStart() {
    acceptAgreement()
    dialog("Complete Installation") {
      radioButton("Do not import settings").select()
      button("OK").click()
    }
    waitWelcomeFrame()
    closeAll()
  }
}