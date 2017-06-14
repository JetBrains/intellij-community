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
package com.intellij.testGuiFramework.launcher

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.launcher.classpath.ClassPathBuilder
import com.intellij.testGuiFramework.launcher.classpath.ClassPathBuilder.Companion.isWin
import com.intellij.testGuiFramework.launcher.classpath.PathUtils
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.util.containers.HashSet
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.Assert.fail
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.JarInputStream
import java.util.stream.Collectors
import kotlin.concurrent.thread

/**
 * @author Sergey Karashevich
 */
object GuiTestLocalLauncher {

  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher")

  var process: Process? = null

  fun killProcessIfPossible() {
    try {
      if (process?.isAlive ?: false) process!!.destroyForcibly()
    }
    catch (e: KotlinNullPointerException) {
      LOG.error("Seems that process has already destroyed, right after condition")
    }
  }

  fun runIdeLocally(ide: Ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0), port: Int = 0) {
    //todo: check that we are going to run test locally
    val args = createArgs(ide = ide, port = port)
    return startIde(ide = ide, args = args)
  }

  fun runIdeByPath(path: String, ide: Ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0), port: Int = 0) {
    //todo: check that we are going to run test locally
    val args = createArgsByPath(path, port)
    return startIde(ide = ide, args = args)
  }

  fun firstStartIdeLocally(ide: Ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0)) {
    val args = createArgsForFirstStart(ide)
    return startIdeAndWait(ide = ide, args = args)
  }

  fun firstStartIdeByPath(path: String, ide: Ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0)) {
    val args = createArgsForFirstStartByPath(ide, path)
    return startIdeAndWait(ide = ide, args = args)
  }

  private fun startIde(ide: Ide,
                       needToWait: Boolean = false,
                       timeOut: Long = 0,
                       timeOutUnit: TimeUnit = TimeUnit.SECONDS,
                       args: List<String>): Unit {
    LOG.info("Running $ide locally \n with args: $args")
    val startLatch = CountDownLatch(1)
    thread(start = true, name = "IdeaTestThread") {
      val ideaStartTest = ProcessBuilder().inheritIO().command(args)
      process = ideaStartTest.start()
      startLatch.countDown()
    }
    if (needToWait) {
      startLatch.await()
      if (timeOut != 0L)
        process!!.waitFor(timeOut, timeOutUnit)
      else
        process!!.waitFor()
      if (process!!.exitValue() != 1) {
        println("${ide.ideType} process completed successfully")
        LOG.info("${ide.ideType} process completed successfully")
      } else {
        System.err.println("${ide.ideType} process execution error:")
        val collectedError = BufferedReader(InputStreamReader(process!!.errorStream)).lines().collect(Collectors.joining("\n"))
        System.err.println(collectedError)
        LOG.error("${ide.ideType} process execution error:")
        LOG.error(collectedError)
        fail("Starting ${ide.ideType} failed.")
      }
    }

  }

  private fun startIdeAndWait(ide: Ide, args: List<String>): Unit
    = startIde(ide = ide, needToWait = true, args = args)


  private fun createArgs(ide: Ide, mainClass: String = "com.intellij.idea.Main", port: Int = 0): List<String>
    = createArgsBase(ide, mainClass, GuiTestStarter.COMMAND_NAME, port)

  private fun createArgsForFirstStart(ide: Ide, port: Int = 0): List<String>
    = createArgsBase(ide, "com.intellij.testGuiFramework.impl.FirstStarterKt", null, port)

  private fun createArgsBase(ide: Ide, mainClass: String, commandName: String?, port: Int): List<String> {
    var resultingArgs = listOf<String>()
      .plus(getCurrentJavaExec())
      .plus(getDefaultVmOptions(ide))
      .plus("-classpath")
      .plus(getOsSpecificClasspath(ide.ideType.mainModule))
      .plus(mainClass)

    if (commandName != null) resultingArgs = resultingArgs.plus(commandName)
    if (port != 0) resultingArgs = resultingArgs.plus("port=$port")
    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")

    return resultingArgs
  }


  private fun createArgsForFirstStartByPath(ide: Ide, path: String): List<String> {

    val classpath = PathUtils(path).makeClassPathBuilder().build(emptyList())
    val resultingArgs = listOf<String>()
      .plus(getCurrentJavaExec())
      .plus(getDefaultVmOptions(ide))
      .plus("-classpath")
      .plus(classpath)
      .plus(com.intellij.testGuiFramework.impl.FirstStarter::class.qualifiedName!! + "Kt")
    return resultingArgs
  }

  private fun createArgsByPath(path: String, port: Int = 0): List<String> {
    var resultingArgs = listOf<String>()
      .plus("open")
      .plus(path) //path to exec
      .plus("--args")
      .plus(GuiTestStarter.COMMAND_NAME)
      .plus("-Didea.additional.classpath=/Users/jetbrains/IdeaProjects/idea-ultimate/out/classes/test/testGuiFramework/")
    if (port != 0) resultingArgs = resultingArgs.plus("port=$port")
    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")
    return resultingArgs
  }


  private fun getDefaultVmOptions(ide: Ide,
                                  configPath: String = "./config",
                                  systemPath: String = "./system",
                                  bootClasspath: String = "./out/classes/production/boot",
                                  encoding: String = "UTF-8",
                                  isInternal: Boolean = true,
                                  useMenuScreenBar: Boolean = true,
                                  debugPort: Int = 5009,
                                  suspendDebug: String = "n"): List<String> =
    listOf<String>()
      .plus("-ea")
      .plus("-Xbootclasspath/p:$bootClasspath")
      .plus("-Dsun.awt.disablegrab=true")
      .plus("-Dsun.io.useCanonCaches=false")
      .plus("-Djava.net.preferIPv4Stack=true")
      .plus("-Dapple.laf.useScreenMenuBar=${useMenuScreenBar.toString()}")
      .plus("-Didea.is.internal=${isInternal.toString()}")
      .plus("-Didea.config.path=$configPath")
      .plus("-Didea.system.path=$systemPath")
      .plus("-Dfile.encoding=$encoding")
      .plus("-Didea.platform.prefix=${ide.ideType.platformPrefix}")
      .plus("-Xdebug")
      .plus(
        "-Xrunjdwp:transport=dt_socket,server=y,suspend=$suspendDebug,address=$debugPort") //todo: add System.getProperty(...) to customize debug port

  private fun getCurrentJavaExec(): String {
    return PathUtils.getJreBinPath()
  }

  private fun getOsSpecificClasspath(moduleName: String): String = ClassPathBuilder.buildOsSpecific(
    getFullClasspath(moduleName).map { it.path })


  /**
   * return union of classpaths for current test (get from classloader) and classpaths of main and testGuiFramework modules*
   */
  private fun getFullClasspath(moduleName: String): List<File> {
    val classpath = getExtendedClasspath(moduleName)
    classpath.addAll(getTestClasspath())
    return classpath.toList()
  }

  private fun getTestClasspath(): List<File> {
    val classLoader = this.javaClass.classLoader
    val urlClassLoaderClass = classLoader.javaClass
    val getUrlsMethod = urlClassLoaderClass.methods.filter { it.name.toLowerCase() == "geturls" }.firstOrNull()!!
    @Suppress("UNCHECKED_CAST")
    val urlsListOrArray = getUrlsMethod.invoke(classLoader)
    var urls = (urlsListOrArray as? List<*> ?: (urlsListOrArray as Array<*>).toList()).filterIsInstance(URL::class.java)
    if (isWin()) {
      val classPathUrl = urls.find { it.toString().contains(Regex("classpath[\\d]*.jar")) }
      if (classPathUrl != null) {
        val jarStream = JarInputStream(File(classPathUrl.path).inputStream())
        val mf = jarStream.manifest
        urls = mf.mainAttributes.getValue("Class-Path").split(" ").map { URL(it) }
      }
    }
    return urls.map { Paths.get(it.toURI()).toFile() }
  }


  /**
   * return union of classpaths for @moduleName and testGuiFramework modules
   */
  private fun getExtendedClasspath(moduleName: String): MutableSet<File> {
    val modules = getModulesList()
    val resultSet = HashSet<File>()
    val module = modules.module(moduleName)
    assert(module != null)
    resultSet.addAll(module!!.getClasspath())
    val testGuiFrameworkModule = modules.module("testGuiFramework")
    assert(testGuiFrameworkModule != null)
    resultSet.addAll(testGuiFrameworkModule!!.getClasspath())
    return resultSet
  }

  private fun List<JpsModule>.module(moduleName: String): JpsModule? =
    this.filter { it.name == moduleName }.firstOrNull()

  private fun JpsModule.getClasspath(): MutableCollection<File> =
    JpsJavaExtensionService.dependencies(this).productionOnly().runtimeOnly().recursively().classes().roots

  private fun getModulesList(): MutableList<JpsModule> {
    val home = PathManager.getHomePath()
    val model = JpsElementFactory.getInstance().createModel()

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, home)

    return model.project.modules
  }
}

fun main(args: Array<String>) {
  GuiTestLocalLauncher.firstStartIdeLocally(Ide(ideType = IdeType.WEBSTORM, version = 0, build = 0))
//  GuiTestLocalLauncher.firstStartIdeByPath("/Users/jetbrains/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/172.2300/IntelliJ IDEA 2017.2 EAP.app")
}