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

import com.intellij.idea.Main
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.util.PlatformUtils.*
import org.fest.swing.core.BasicRobot
import org.fest.swing.core.ComponentMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.JRadioButtonFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Toolkit
import java.util.concurrent.TimeUnit
import javax.swing.JRadioButton

/**
 * @author Sergey Karashevich
 */
class FirstStartToRemove(robot: Robot) : GuiTestCase() {

  fun webstormBeforeStart() {
    dialog("Complete Installation") { button("OK").click() }
  }

  fun webstormAfterStart() {
    dialog("WebStorm Initial Configuration") { button("OK").click() }
  }

  fun idea(): Unit = TODO("Create complete installation case for IntelliJ IDEA")

  fun completeBefore() {

    when (System.getProperty(PLATFORM_PREFIX_KEY, IDEA_PREFIX)) {
      IDEA_PREFIX -> TODO("Create complete installation case for IntelliJ IDEA")
      IDEA_CE_PREFIX -> TODO("Create complete installation case for IntelliJ IDEA Community Edition")
      APPCODE_PREFIX -> TODO("Create complete installation case for AppCode")
      CLION_PREFIX -> TODO("Create complete installation case for CLion")
      PYCHARM_PREFIX -> TODO("Create complete installation case for PyCharm")
      PYCHARM_CE_PREFIX -> TODO("Create complete installation case for PyCharm Community Edition")
      PYCHARM_EDU_PREFIX -> TODO("Create complete installation case for PyCharm Educational Edition")
      RUBY_PREFIX -> TODO("Create complete installation case for RubyMine")
      PHP_PREFIX -> TODO("Create complete installation case for PhpStorm")
      WEB_PREFIX -> webstormBeforeStart()
      DBE_PREFIX -> TODO("Create complete installation case for DataGrip")
      RIDER_PREFIX -> TODO("Create complete installation case for Rider")
      GOIDE_PREFIX -> TODO("Create complete installation case for Gogland")
      else -> TODO("Unsupported IDE prefix")
    }
  }

  fun completeAfter() {

    when (System.getProperty(PLATFORM_PREFIX_KEY, IDEA_PREFIX)) {
      IDEA_PREFIX -> TODO("Create complete installation case for IntelliJ IDEA")
      IDEA_CE_PREFIX -> TODO("Create complete installation case for IntelliJ IDEA Community Edition")
      APPCODE_PREFIX -> TODO("Create complete installation case for AppCode")
      CLION_PREFIX -> TODO("Create complete installation case for CLion")
      PYCHARM_PREFIX -> TODO("Create complete installation case for PyCharm")
      PYCHARM_CE_PREFIX -> TODO("Create complete installation case for PyCharm Community Edition")
      PYCHARM_EDU_PREFIX -> TODO("Create complete installation case for PyCharm Educational Edition")
      RUBY_PREFIX -> TODO("Create complete installation case for RubyMine")
      PHP_PREFIX -> TODO("Create complete installation case for PhpStorm")
      WEB_PREFIX -> webstormAfterStart()
      DBE_PREFIX -> TODO("Create complete installation case for DataGrip")
      RIDER_PREFIX -> TODO("Create complete installation case for Rider")
      GOIDE_PREFIX -> TODO("Create complete installation case for Gogland")
      else -> TODO("Unsupported IDE prefix")
    }
  }

  init {
    super.setRobot(robot)
  }

  companion object {


    @JvmStatic fun main(args: Array<String>) {

      object : Thread("GUI Test Thread") {

        fun startRobotActivity() {
          val myRobot = BasicRobot.robotWithNewAwtHierarchyWithoutScreenLock()
          val dialogCompleteInstallation = JDialogFixture.find(myRobot, "Complete Installation")
          val foundComponent: JRadioButton = myRobot.finder().find { component -> component is JRadioButton && component.text.equals("Do not import settings") } as JRadioButton
          JRadioButtonFixture(myRobot, foundComponent).click()
          GuiTestUtil.findAndClickButton(dialogCompleteInstallation, "OK")

          val dialogUiCustom = JDialogFixture.find(myRobot, "Customize IntelliJ IDEA")
          GuiTestUtil.findAndClickButton(dialogUiCustom, "Skip All and Set Defaults")

          Pause.pause(object: Condition("Waiting for welcome frame") {
            override fun test(): Boolean {
              try {
                return myRobot.finder().findAll(ComponentMatcher { component -> component!!.javaClass.name == "com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame" }).size == 1
              } catch (cle: ComponentLookupException) {
                return false
              }
            }
          }, Timeout.timeout(1, TimeUnit.MINUTES))
          reflectiveExit()
          myRobot.cleanUp()
        }

        private fun reflectiveExit() {
          val appManager = Toolkit.getDefaultToolkit().systemEventQueue.javaClass.classLoader.loadClass(ApplicationManager::class.java.name)
          val getAppMethod = appManager.getMethod("getApplication")
          val app = getAppMethod.invoke(null)
          app.javaClass.getMethod("exit").invoke(app)
        }

        fun awtIsStarted() = Thread.getAllStackTraces().keys.any { thread -> thread.name.toLowerCase().contains("awt") }

        override fun run() {
          while (!awtIsStarted()) Thread.sleep(100) //we need to wait when awt thread will appear to avoid create it with wrong classloader
          startRobotActivity()
        }
      }.start()
      Main.main(args)
    }
  }

}