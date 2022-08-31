// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e

import com.intellij.ide.starter.extended.commands.code
import com.intellij.ide.starter.extended.data.TestCases
import com.intellij.ide.starter.extended.engine.junit4.initJUnit4ContainerExtended
import com.intellij.ide.starter.extended.runner.ExecutableCode
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.uiTests.robot.StepPrinter
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.java.rebuild
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

class UiDemoTest {
  @Rule
  @JvmField
  val testContextFactory = initJUnit4ContainerExtended().withSetupHook  {
    this.apply {
      pluginConfigurator.setupPluginFromURL(
        urlToPluginZipFile = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/com/intellij/remoterobot/robot-server-plugin/0.11.16/robot-server-plugin-0.11.16.zip")
    }
  }

  @Test
  fun clickProjectAndSourceAfterRebuild() {
    val lockFileName = "uiLock.txt"
    val project = TestCases.IU.JavaProjectWithJdk8
    val context = testContextFactory.initializeTestContext("ui-test", project).addLockFileForUITest(lockFileName)
    val uiLockTempFile = context.paths.tempDir / lockFileName

    val launchName = "rebuildWithUI"
    uiLockTempFile.delete()

    Waiter.checkCondition {
      if (uiLockTempFile.exists()) {
        if (uiLockTempFile.readText() == "ready") {
          val robot = RemoteRobot("http://127.0.0.1:8580/").apply {
            StepWorker.registerProcessor(StepPrinter())
          }
          println("remote robot os: ${robot.os}")
          val projectButton = robot.find<ComponentFixture>(
            byXpath("//div[@text='Project']"))
          val structureButton = robot.find<ComponentFixture>(
            byXpath("//div[@text='Structure']"))
          projectButton.click()
          structureButton.click()
          projectButton.click()

          uiLockTempFile.writeText("go")
          return@checkCondition true
        }
      }
      return@checkCondition false
    }

    val commandsUi: CommandChain = CommandChain()
      .rebuild()
      .code(
        ExecutableCode.fromLambda {
          val uiLockTempFileInsideIDE = File(System.getProperty("uiLockTempFile"))
          uiLockTempFileInsideIDE.writeText("ready")
          var flag = false
          while (!flag) {
            Thread.sleep(100L)
            if (uiLockTempFileInsideIDE.readText() == "go") flag = true
          }
        })
      .exitApp()

    context.runIDE(launchName = launchName, commands = commandsUi, runTimeout = 10.minutes)
  }
}

object Waiter {
  private const val DELAY = 100L
  fun checkCondition(function: BooleanSupplier): CountDownLatch {
    val latch = CountDownLatch(1)
    val executor: ScheduledExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin waiter")
    executor.scheduleWithFixedDelay({
                                      if (function.asBoolean) {
                                        latch.countDown()
                                        executor.shutdown()
                                      }
                                    }, 0, DELAY, TimeUnit.MILLISECONDS)
    return latch
  }
}