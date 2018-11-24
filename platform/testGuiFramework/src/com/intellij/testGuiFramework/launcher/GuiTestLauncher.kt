package com.intellij.testGuiFramework.launcher

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.launcher.classpath.ClassPathBuilder
import com.intellij.testGuiFramework.launcher.file.PathManager
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeRunArgs
import com.intellij.testGuiFramework.launcher.ide.IdeTestFixture
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.CUSTOM_CONFIG_PATH
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.FEST_LIB_PATH
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.GUI_TEST_DATA_DIR
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.JDK_PATH
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.JUNIT_PATH
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.PLATFORM_PREFIX
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.TEST_CLASSES_DIR
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.TEST_GUI_FRAMEWORK_PATH
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.junit.Assert
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

/**
 * @author Sergey Karashevich
 */

object GuiTestLauncher {

  val JUNIT_STARTER by lazy { "org.junit.runner.JUnitCore" }
  val javaExec by lazy { "${JDK_PATH}${File.separator}bin${File.separator}java" }
  val SET_GUI_TEST_DATA_DIR by lazy { "-DGUI_TEST_DATA_DIR=${GUI_TEST_DATA_DIR}" }
  val SET_CUSTOM_CONFIG_PATH by lazy { "-DCUSTOM_CONFIG_PATH=${CUSTOM_CONFIG_PATH ?: ""}" }

  val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.launcher.GuiTestLauncher")

  fun createArgs(ideaLibPath: String, testClass: String, testClassPath: String): List<String> {
    val classpath = ClassPathBuilder(ideaLibPath, JDK_PATH, JUNIT_PATH, FEST_LIB_PATH, TEST_GUI_FRAMEWORK_PATH).build(testClassPath)
    val resultingArgs = listOf(javaExec,
                               "-Didea.platform.prefix=${PLATFORM_PREFIX}",
                               SET_GUI_TEST_DATA_DIR,
                               SET_CUSTOM_CONFIG_PATH,
                               "-classpath",
                               classpath,
                               JUNIT_STARTER,
                               testClass)
    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")
    return resultingArgs
  }

  fun createArgs(ideaLibPath: String, testClasses: List<String>, testClassPath: String): List<String> {
    val classpath = ClassPathBuilder(ideaLibPath, JDK_PATH, JUNIT_PATH, FEST_LIB_PATH, TEST_GUI_FRAMEWORK_PATH).build(testClassPath)
    val resultingArgs = listOf(javaExec,
                               "-Didea.platform.prefix=${PLATFORM_PREFIX}",
                               SET_GUI_TEST_DATA_DIR,
                               SET_CUSTOM_CONFIG_PATH,
                               "-classpath",
                               classpath,
                               JUNIT_STARTER,
                               testClasses.joinToString(", "))
    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")
    return resultingArgs
  }

  fun createArgs(ideaLibPath: String, ideRunArgs: IdeRunArgs): List<String> {
    val classpath = ClassPathBuilder(ideaLibPath, JDK_PATH, JUNIT_PATH, FEST_LIB_PATH, TEST_GUI_FRAMEWORK_PATH).build(TEST_CLASSES_DIR)
    val resultingArgs = listOf<String>(javaExec)
      .plus(ideRunArgs.jvmParams)
      .plus(ideRunArgs.ideParams)
      .plus("-classpath")
      .plus(classpath)
      .plus(JUNIT_STARTER)
      .plus(ideRunArgs.testClasses)
    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")
    return resultingArgs
  }

  fun runTest(ide: Ide, testClassAndMethod: String) {
    val testClassName = testClassAndMethod.split("#")[0]
    val testMethodName = testClassAndMethod.split("#")[1]
    val testClass = Class.forName(testClassName)
    if (!JUnitServerHolder.getServer().isConnected()) //if not connected to JUnitClient
      GuiTestLocalLauncher.runIdeLocally(port = JUnitServerHolder.getServer().getPort(), ide = ide) //todo: add IDE specification here
    val jUnitTestContainer = JUnitTestContainer(testClass, testMethodName)
    JUnitServerHolder.getServer().send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
  }

  fun runIde(ide: Ide, ideRunArgs: IdeRunArgs): Unit {
    LOG.info("Running $ide with $ideRunArgs")
    val pathToSave = PathManager.getWorkDirPath()
    LOG.info("IDE path to save is set to: $pathToSave")
    val runnable: () -> Unit = {
      val ideaStartTest = ProcessBuilder().inheritIO().command(
        createArgs(ideaLibPath = PathManager.getSystemSpecificIdeLibPath(pathToSave), ideRunArgs = ideRunArgs))
      val process = ideaStartTest.start()
      val wait = process.waitFor()
      if (process.exitValue() != 1) println("Execution successful")
      else {
        Assert.fail("Process execution error:")
        Assert.fail(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n")))
      }
    }
    val ideaTestThread = Thread(runnable, "IdeaTestThread")
    ideaTestThread.start()
  }

  fun runIde(ide: Ide, testClasses: List<String>): Unit {
    LOG.info("Running $ide for test classes: ${testClasses.joinToString(", ")}")
    val pathToSave = PathManager.getWorkDirPath()
    val runnable: () -> Unit = {
      val args = createArgs(ideaLibPath = PathManager.getSystemSpecificIdeLibPath(pathToSave), testClasses = testClasses,
                            testClassPath = TEST_CLASSES_DIR)
      val ideaStartTest = ProcessBuilder().inheritIO().command(args)
      val process = ideaStartTest.start()
      val wait = process.waitFor()
      if (process.exitValue() != 1) println("Execution successful")
      else {
        System.err.println("Process execution error:")
        System.err.println(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n")))
      }
    }
    val ideaTestThread = Thread(runnable, "IdeaTestThread")
    ideaTestThread.start()
  }

  fun runIdeLocally(ide: Ide, args: List<String>) {
    LOG.info("Running $ide locally")
    val runnable: () -> Unit = {
      val ideaStartTest = ProcessBuilder().inheritIO().command(args)
      val process = ideaStartTest.start()
      val wait = process.waitFor()
      if (process.exitValue() != 1) println("Execution successful")
      else {
        System.err.println("Process execution error:")
        System.err.println(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n")))
      }
    }
    val ideaTestThread = Thread(runnable, "IdeaTestThread")
    ideaTestThread.start()
  }

  fun runIde(ide: Ide, func: IdeTestFixture.() -> Unit) {
    val ideTestFixture = IdeTestFixture(ide)
    func(ideTestFixture)
    runIde(ide, ideTestFixture.buildArgs())
  }

  fun multiDpiTest(ide: Ide, dpiScale: String, func: IdeTestFixture.() -> Unit) {
    val ideTestFixture = IdeTestFixture(ide)
    func(ideTestFixture)
    runIde(ide, ideTestFixture.buildArgs())
    ideTestFixture.jvmConfig(listOf("-Dsun.java2d.uiScale.enabled=true", "-Dsun.java2d.uiScale=$dpiScale"))
    runIde(ide, ideTestFixture.buildArgs())
  }

  fun multiDpiTest(ide: Ide, dpiScale: Float, func: IdeTestFixture.() -> Unit) = multiDpiTest(ide, dpiScale.toString(), func)

}