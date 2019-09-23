// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.launcher

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.launcher.classpath.ClassPathBuilder.Companion.toClasspathJarFile
import com.intellij.testGuiFramework.launcher.classpath.PathUtils
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.remote.IdeControl
import com.intellij.util.PathUtil
import org.apache.log4j.Logger
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.Assert.fail
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.JarInputStream
import java.util.stream.Collectors
import kotlin.concurrent.thread


/**
 * @author Sergey Karashevich
 */
object GuiTestLocalLauncher {

  private val LOG: Logger = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestSuiteRunner")

  private const val TEST_GUI_FRAMEWORK_MODULE_NAME = "intellij.platform.testGuiFramework"

  val project: JpsProject by lazy {
    val home = PathManager.getHomePath()
    val model = JpsElementFactory.getInstance().createModel()
    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    val jpsProject = model.project
    JpsProjectLoader.loadProject(jpsProject, pathVariables, home)
    jpsProject.changeOutputIfNeeded()
    jpsProject
  }
  private val modulesList: List<JpsModule> by lazy {
    project.modules
  }
  private val testGuiFrameworkModule: JpsModule by lazy {
    modulesList.module(TEST_GUI_FRAMEWORK_MODULE_NAME) ?: throw Exception("Unable to find module '$TEST_GUI_FRAMEWORK_MODULE_NAME'")
  }


  /**
   * @additionalJvmOptions - an array of key-value pairs written without -D, for example: {@code arrayOf(Pair("idea.debug.mode", "true"))
   * By default set as an empty array – no additional JVM options
   */
  fun runIdeLocally(ide: Ide = Ide(CommunityIde(), 0, 0),
                    port: Int = 0,
                    testClassNames: List<String> = emptyList(),
                    additionalJvmOptions: List<Pair<String, String>> = emptyList()) {
    val args = createArgs(ide = ide, port = port, testClassNames = testClassNames, additionalJvmOptions = additionalJvmOptions)
    return startIde(ide = ide, ideaStartTest = ProcessBuilder().inheritIO().command(args))
  }

  fun runIdeWithDTraceLocally(ide: Ide = Ide(CommunityIde(), 0, 0),
                              port: Int = 0,
                              testClassName: String = "",
                              additionalJvmOptions: List<Pair<String, String>> = emptyList()) {

    val args = createArgs(ide = ide, port = port, testClassNames = listOf(testClassName), additionalJvmOptions = additionalJvmOptions)

    val margs = arrayOfNulls<String>(args.size)
    for (i in 0 until margs.size) {
      margs[i] = args[i]
    }

    for (i in 0 until margs.size) {
      if (margs[i]!!.contains("-classpath")) {
        margs[i + 1] = margs[i + 1]!!.replace(" ", "\\ ")
        println(margs[i + 1])
        break
      }
    }

    val klass = Class.forName(testClassName)
    val testInstance = klass.newInstance()

    val testSrcPathMethod = klass.getMethod("getTestSrcPath")
    val testSrcPath = testSrcPathMethod.invoke(testInstance) as String

    val testDTScriptNameMethod = klass.getMethod("getDTScriptName")
    val testDTScriptName = testDTScriptNameMethod.invoke(testInstance) as String

    LOG.info("dtrace script: ${testSrcPath}${File.separatorChar}${testDTScriptName}")

    val modArgs = listOf<String>()
      .plus("dtrace")
      .plus("-Z")
      //      .plus("-q")
      .plus("-s")
      .plus("${testSrcPath}${File.separatorChar}${testDTScriptName}")
      .plus("-c")
      .plus(margs.joinToString(" "))
    return startIde(ide = ide, ideaStartTest = ProcessBuilder().command(modArgs))
  }

  fun runIdeByPath(path: String, ide: Ide = Ide(CommunityIde(), 0, 0), port: Int = 0) {
    //todo: check that we are going to run test locally
    val args = createArgsByPath(path, port)
    return startIde(ide = ide, ideaStartTest = ProcessBuilder().inheritIO().command(args))
  }

  fun firstStartIdeLocally(ide: Ide = Ide(CommunityIde(), 0, 0), firstStartClassName: String = "undefined", additionalJvmOptions: List<Pair<String, String>> = emptyList()) {
    val args = createArgsForFirstStart(ide = ide, firstStartClassName = firstStartClassName, additionalJvmOptions = additionalJvmOptions)
    return startIdeAndWait(ide = ide, args = args)
  }

  private fun startIde(ide: Ide,
                       needToWait: Boolean = false,
                       timeOut: Long = 0,
                       timeOutUnit: TimeUnit = TimeUnit.SECONDS,
                       ideaStartTest: ProcessBuilder) {
    LOG.info("Running $ide locally \n with args: ${ideaStartTest.command().joinToString(" ")}")
    //do not limit IDE starting if we are using debug mode to not miss the debug listening period
    val conditionalTimeout = if (GuiTestOptions.isDebug) 0 else timeOut
    val startLatch = CountDownLatch(1)
    thread(start = true, name = "IdeaThread") {
      IdeControl.submitIdeProcess(ideaStartTest.start())
      startLatch.countDown()
    }
    if (needToWait) {
      startLatch.await()
      if (conditionalTimeout != 0L)
        IdeControl.waitForCurrentProcess(conditionalTimeout, timeOutUnit)
      else
        IdeControl.waitForCurrentProcess()
      try {
        if (IdeControl.exitValue() == 0) {
          println("${ide.ideType} processStdIn completed successfully")
          LOG.info("${ide.ideType} processStdIn completed successfully")
        }
        else {
          System.err.println("${ide.ideType} processStdIn execution error:")
          val collectedError = BufferedReader(InputStreamReader(IdeControl.getErrorStream())).lines().collect(
            Collectors.joining("\n"))
          System.err.println(collectedError)
          LOG.error("${ide.ideType} processStdIn execution error:")
          LOG.error(collectedError)
          fail("Starting ${ide.ideType} failed.")
        }
      }
      catch (e: IllegalThreadStateException) {
        IdeControl.closeIde()
        throw e
      }
    }

  }

  private fun startIdeAndWait(ide: Ide, args: List<String>) = startIde(ide = ide, needToWait = true, timeOut = 240,
                                                                       ideaStartTest = ProcessBuilder().inheritIO().command(args))


  /**
   * @additionalJvmOptions - an array of key-value pairs written without -D, for example: {@code arrayOf(Pair("idea.debug.mode", "true"))
   * By default set as an empty array – no additional JVM options
   */
  private fun createArgs(ide: Ide,
                         mainClass: String = "com.intellij.idea.Main",
                         port: Int = 0,
                         testClassNames: List<String>,
                         additionalJvmOptions: List<Pair<String, String>> = emptyList()):
    List<String> = createArgsBase(ide = ide,
                                  mainClass = mainClass,
                                  commandName = GuiTestStarter.COMMAND_NAME,
                                  port = port,
                                  testClassNames = testClassNames,
                                  additionalJvmOptions = additionalJvmOptions)

  private fun createArgsForFirstStart(ide: Ide, firstStartClassName: String = "undefined", port: Int = 0, additionalJvmOptions: List<Pair<String, String>> = emptyList()): List<String> = createArgsBase(
    ide = ide,
    mainClass = "com.intellij.testGuiFramework.impl.FirstStarterKt",
    firstStartClassName = firstStartClassName,
    commandName = null,
    port = port,
    testClassNames = emptyList(),
    additionalJvmOptions = additionalJvmOptions)

  /**
   * customVmOptions should contain a full VM options formatted items like: customVmOptions = listOf("-Dapple.laf.useScreenMenuBar=true", "-Dide.mac.file.chooser.native=false").
   * GuiTestLocalLauncher passed all VM options from test, that starts with "-Dpass."
   *
   * @additionalJvmOptions - an array of key-value pairs written without -D, for example: {@code arrayOf(Pair("idea.debug.mode", "true"))
   * By default set as an empty array – no additional JVM options
   */
  private fun createArgsBase(ide: Ide,
                             mainClass: String,
                             commandName: String?,
                             firstStartClassName: String = "undefined",
                             port: Int,
                             testClassNames: List<String>,
                             additionalJvmOptions: List<Pair<String, String>> = emptyList()): List<String> {
    val customVmOptions = getCustomPassedOptions()
    var resultingArgs = listOf<String>()
      .plus(getCurrentJavaExec())
      .plus(getDefaultAndCustomVmOptions(ide, customVmOptions))
      .plus(additionalJvmOptions.map { val (key, value) = it; "-D$key=$value" })
      .plus("-Didea.gui.test.first.start.class=$firstStartClassName")
      .plus("-classpath")
      .plus(composeClasspathJar(ide.ideType.mainModule, testClassNames))
      .plus(mainClass)

    if (commandName != null) resultingArgs = resultingArgs.plus(commandName)
    if (port != 0) resultingArgs = resultingArgs.plus("port=$port")
    //    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")

    return resultingArgs
  }

  private fun createArgsByPath(path: String, port: Int = 0): List<String> {
    val resultingArgs = mutableListOf(
      path,
      "--args",
      GuiTestStarter.COMMAND_NAME
    )
    if (SystemInfo.isMac()) resultingArgs.add(0, "open")
    if (port != 0) resultingArgs.add("port=$port")
    //    LOG.info("Running with args: ${resultingArgs.joinToString(" ")}")
    return resultingArgs
  }

  private fun getCurrentProcessVmOptions(): List<String> {
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
    return runtimeMxBean.inputArguments
  }

  private fun getPassedVmOptions(): List<String> {
    return getCurrentProcessVmOptions().filter { it.startsWith("-Dpass.") }
  }

  private fun getCustomPassedOptions(): List<String> {
    return getPassedVmOptions().map { it.replace("-Dpass.", "-D") }
  }

  /**
   * Default VM options to start IntelliJ IDEA (or IDEA-based IDE). To customize options use com.intellij.testGuiFramework.launcher.GuiTestOptions
   */
  private fun getDefaultAndCustomVmOptions(ide: Ide, customVmOptions: List<String> = emptyList()): List<String> {
    return listOf<String>()
      .plusIf(GuiTestOptions.xssSize != 0, "-Xss${GuiTestOptions.xssSize}m")
      .plus("-Xmx${GuiTestOptions.xmxSize}m")
      .plus("-XX:ReservedCodeCacheSize=240m")
      .plus("-XX:+UseG1GC")
      .plus("-XX:SoftRefLRUPolicyMSPerMB=50")
      .plus("-XX:MaxJavaStackTraceDepth=10000")
      .plus("-ea")
      .plus("-Xbootclasspath/p:${GuiTestOptions.bootClasspath}")
      .plus("-Dsun.awt.disablegrab=true")
      .plus("-Dsun.io.useCanonPrefixCache=false")
      .plus("-Djava.net.preferIPv4Stack=true")
      .plus("-Dapple.laf.useScreenMenuBar=${GuiTestOptions.useAppleScreenMenuBar}")
      .plus("-Didea.is.internal=${GuiTestOptions.isInternal}")
      .plus("-Didea.debug.mode=true")
      .plus("-Dide.mac.file.chooser.native=false")
      .plus("-Didea.config.path=${GuiTestOptions.configPath}")
      .plus("-Didea.system.path=${GuiTestOptions.systemPath}")
      .plus("-Didea.log.config.file=${GuiTestOptions.guiTestLogFile}")
      .plus("-Dfile.encoding=${GuiTestOptions.encoding}")
      .plus("-Djb.privacy.policy.text=<!--999.999-->")
      .plus("-Djb.consents.confirmation.enabled=false")
      .plusIf(System.getProperty("java.io.tmpdir") != null, "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}")
      .plusIf(!ide.ideType.platformPrefix.isNullOrEmpty(), "-Didea.platform.prefix=${ide.ideType.platformPrefix}")
      .plus(ide.ideType.ideSpecificOptions)
      .plus(customVmOptions)
      .plus("-Xdebug")
      .plus("-Xrunjdwp:transport=dt_socket,server=y,suspend=${GuiTestOptions.suspendDebug},address=${GuiTestOptions.debugPort}")
      .plus("-Duse.linux.keychain=false")
      .plus("-Didea.suppress.statistics.report")
  }

  private fun getCurrentJavaExec(): String {
    return PathUtils.getJreBinPath()
  }

  private fun composeClasspathJar(moduleName: String, testClassNames: List<String>): String =
    getFullClasspath(moduleName, testClassNames).map { it.path }.toClasspathJarFile().path


  /**
   * return union of classpaths for current test (get from classloader) and classpaths of main and intellij.platform.testGuiFramework modules*
   */
  private fun getFullClasspath(moduleName: String, testClassNames: List<String>): List<File> {
    val classpath: MutableSet<File> = substituteAllMacro(getExtendedClasspath(moduleName))
    classpath.addAll(getTestClasspath(testClassNames))
    classpath.add(getToolsJarFile())
    return classpath.toList()
  }

  private fun getToolsJarFile(): File = File(PathUtil.getParentPath(System.getProperty("java.home")), "lib${File.separator}tools.jar")

  /**
   * Finds in a current classpath that built from a test module dependencies resolved macro path
   * macroName = "\$MAVEN_REPOSITORY\$"
   */
  private fun resolveMacro(classpath: MutableSet<File>, macroName: String): String {
    val pathWithMacro = classpath.firstOrNull { it.startsWith(macroName) }?.path ?: throw Exception(
      "Unable to find file in a classpath starting with next macro: '$macroName'")
    val tailOfPathWithMacro = pathWithMacro.substring(macroName.length)
    val urlPaths = getUrlPathsFromClassloader()
    val fullPathWithResolvedMacro = urlPaths.firstOrNull { it.endsWith(tailOfPathWithMacro) } ?: throw Exception(
      "Unable to find in classpath URL with the next tail: $tailOfPathWithMacro")
    return fullPathWithResolvedMacro.substring(0..(fullPathWithResolvedMacro.length - tailOfPathWithMacro.length))
  }

  private fun substituteAllMacro(classpath: MutableSet<File>): MutableSet<File> {
    val macroList = listOf("\$MAVEN_REPOSITORY\$")
    val macroMap = mutableMapOf<String, String>()
    macroList.forEach { macroMap[it] = resolveMacro(classpath, it) }
    val mutableClasspath = mutableListOf<File>()
    classpath.forEach { file ->
      val macro = file.path.findStartsWith(macroList)
      if (macro != null) {
        val resolvedMacro = macroMap[macro]
        val newPath = resolvedMacro + file.path.substring(macro.length + 1)
        mutableClasspath.add(File(newPath))
      }
      else mutableClasspath.add(file)
    }
    return mutableClasspath.toMutableSet()
  }

  private fun String.findStartsWith(list: List<String>): String? {
    return list.find { this.startsWith(it) }
  }

  private fun getUrlPathsFromClassloader(): List<String> {
    val classLoader = this.javaClass.classLoader
    val urlClassLoaderClass = classLoader.javaClass
    val getUrlsMethod = urlClassLoaderClass.methods.firstOrNull { it.name.toLowerCase() == "geturls" }!!
    @Suppress("UNCHECKED_CAST")
    val urlsListOrArray = getUrlsMethod.invoke(classLoader)
    var urls = (urlsListOrArray as? List<*> ?: (urlsListOrArray as Array<*>).toList()).filterIsInstance(URL::class.java)
    val classPathUrl = urls.find { it.toString().contains(Regex("classpath[\\d]*.jar")) }
    if (classPathUrl != null) {
      val jarStream = JarInputStream(File(classPathUrl.path).inputStream())
      val mf = jarStream.manifest
      urls = mf.mainAttributes.getValue("Class-Path").split(" ").map { URL(it) }
    }
    return urls.map { Paths.get(it.toURI()).toFile().path }
  }

  private fun getTestClasspath(testClassNames: List<String>): List<File> {
    if (testClassNames.isEmpty()) return emptyList()
    val fileSet = mutableSetOf<File>()
    testClassNames.forEach {
      fileSet.add(getClassFile(it))
    }
    return fileSet.toList()
  }


  /**
   * returns a file (directory or a jar) containing class loaded by a class loader with a given name
   */
  private fun getClassFile(className: String): File {
    val classLoader = this.javaClass.classLoader
    val cls = classLoader.loadClass(className) ?: throw Exception(
      "Unable to load class ($className) with a given classloader. Check the path to class or a classloader URLs.")
    val name = "${cls.simpleName}.class"
    val packagePath = cls.`package`.name.replace(".", "/")
    val fullPath = "$packagePath/$name"
    val resourceUrl = classLoader.getResource(fullPath) ?: throw Exception(
      "Unable to get resource path to a \"$fullPath\". Check the path to class or a classloader URLs.")
    val correctPath = resourceUrl.correctPath()
    var cutPath = correctPath.substring(0, correctPath.length - fullPath.length)
    if (cutPath.endsWith("!") or cutPath.endsWith("!/")) cutPath = cutPath.substring(0..(cutPath.length - 3)) // in case of it is a jar
    val file = File(cutPath)
    if (!file.exists()) throw Exception("File for a class '$className' doesn't exist by path: $cutPath")
    return file
  }


  /**
   * return union of classpaths for @moduleName and intellij.platform.testGuiFramework modules
   */
  private fun getExtendedClasspath(moduleName: String): MutableSet<File> {
    // here we trying to analyze output path for project from classloader path and from modules classpath.
    // If they didn't match than change it to output path from classpath
    val resultSet = LinkedHashSet<File>()
    val module = modulesList.module(moduleName) ?: throw Exception("Unable to find module with name: $moduleName")
    resultSet.addAll(module.getClasspath())
    return resultSet
  }

  private fun List<JpsModule>.module(moduleName: String): JpsModule? =
    this.firstOrNull { it.name == moduleName }

  //get production dependencies and test root of the current module
  private fun JpsModule.getClasspath(): MutableCollection<File> {
    val result = JpsJavaExtensionService.dependencies(
      this).productionOnly().runtimeOnly().recursively().classes().roots.toMutableSet()
    result.addAll(JpsJavaExtensionService.dependencies(this).withoutDepModules().withoutLibraries().withoutSdk().classes().roots)
    return result.toMutableList()
  }


  private fun getOutputRootFromClassloader(): File {
    val pathFromClassloader = PathManager.getJarPathForClass(GuiTestLocalLauncher::class.java)
    val productionDir = File(pathFromClassloader).parentFile
    assert(productionDir.isDirectory)
    val outputDir = productionDir.parentFile
    assert(outputDir.isDirectory)
    return outputDir
  }

  /**
   * @return true if classloader's output path is the same to module's output path (and also same to project)
   */
  private fun needToChangeProjectOutput(project: JpsProject): Boolean =
    JpsJavaExtensionService.getInstance().getProjectExtension(project)?.outputUrl ==
      getOutputRootFromClassloader().path


  private fun JpsProject.changeOutputIfNeeded() {
    if (!needToChangeProjectOutput(this)) {
      val projectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(this)
      projectExtension.outputUrl = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(getOutputRootFromClassloader().path))
    }
  }

  private fun URL.correctPath(): String {
    return Paths.get(this.toURI()).toFile().path
  }

  /**
   * Returns a list containing all elements of the original collection including the given [element] if [condition] is true.
   */
  private fun <T> List<T>.plusIf(condition: Boolean, element: T): List<T> {
    if (!condition) return this
    val result = ArrayList<T>(size + 1)
    result.addAll(this)
    result.add(element)
    return result
  }


}