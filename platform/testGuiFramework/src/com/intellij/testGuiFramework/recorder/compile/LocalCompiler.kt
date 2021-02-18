// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder.compile

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.recorder.GuiRecorderManager
import com.intellij.testGuiFramework.recorder.ui.Notifier
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.lang.UrlClassLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

private val LOG by lazy { Logger.getInstance(LocalCompiler::class.java) }
private const val TEST_CLASS_NAME = "CurrentTest"

/**
 * @author Sergey Karashevich
 */
internal class LocalCompiler {
  private var codeHash: Int? = null

  private val helloKtText = "fun main(args: Array<String>) { \n println(\"Hello, World!\") \n }"
  private val kotlinCompilerJarUrl = "http://central.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler/1.0.6/kotlin-compiler-1.0.6.jar"
  private val kotlinCompilerJarName = "kotlin-compiler-1.0.6.jar"

  private val tempDir by lazy { FileUtil.createTempDirectory("kotlin-compiler-tmp", null, true) }
  private val helloKt by lazy { createTempFile(helloKtText) }
  private var tempFile: File? = null

  private fun createTempFile(content: String, fileName: String = TEST_CLASS_NAME, extension: String = ".kt"): File {
    val tempFile = FileUtil.createTempFile(fileName, extension, true)
    FileUtil.writeToFile(tempFile, content, false)
    this.tempFile = tempFile
    return tempFile
  }

  fun compileAndRunOnPooledThread(code: String, classpath: List<String>) {
    val taskFuture = ApplicationManager.getApplication().executeOnPooledThread(
      {
        try {
          if (codeHash == null || codeHash != code.hashCode()) {
            compile(code, classpath)
            codeHash = code.hashCode()
          }
          run()
        }
        catch (ce: CompilationException) {
          LOG.error(ce.message)
        }
      })
    GuiRecorderManager.currentTask = taskFuture

  }

  //alternative way to run compiled code with pluginClassloader built especially for this file
  private fun run() {
    Notifier.updateStatus("${Notifier.LONG_OPERATION_PREFIX}Script running...")
    var classLoadersArray: Array<ClassLoader>
    try {
      //run testGuiTest gradle configuration
      ApplicationManager::class.java.classLoader.loadClass("com.intellij.testGuiFramework.impl.GuiTestCase")
      classLoadersArray = arrayOf(ApplicationManager::class.java.classLoader)
    }
    catch (cfe: ClassNotFoundException) {
      classLoadersArray = arrayOf(ApplicationManager::class.java.classLoader, this.javaClass.classLoader)
    }
    val pluginClassLoader = PluginClassLoader(UrlClassLoader.build().files(listOf(tempDir.toPath())).useCache(),
                                              classLoadersArray, DefaultPluginDescriptor("SubGuiScriptRecorder"), null as Path?,
                                              PluginManagerCore::class.java.classLoader, null, null, null)
    val currentTest = pluginClassLoader.loadClass(TEST_CLASS_NAME)
                      ?: throw Exception("Unable to load by pluginClassLoader $TEST_CLASS_NAME.class file")
    val testCase = currentTest.getDeclaredConstructor().newInstance()
    val testMethod = currentTest.getMethod(ScriptWrapper.TEST_METHOD_NAME)
    GuiRecorderManager.state = GuiRecorderManager.States.RUNNING
    try {
      testMethod.invoke(testCase)
      Notifier.updateStatus("Script stopped")
      GuiRecorderManager.state = GuiRecorderManager.States.IDLE
    }
    catch (throwable: Throwable) {
      GuiRecorderManager.state = GuiRecorderManager.States.RUNNING_ERROR
      Notifier.updateStatus("Running error, please see idea.log")
      throw throwable
    }

  }

  private fun compile(code: String, classpath: List<String>): Boolean = compile(createTempFile(code), classpath)

  private fun compile(fileKt: File? = null, classpath: List<String>): Boolean {
    val scriptKt = fileKt ?: helloKt
    val kotlinCompilerJar = getKotlinCompilerJar()
    val libDirLocation = getApplicationLibDir().parentFile

    Notifier.updateStatus("${Notifier.LONG_OPERATION_PREFIX}Compiling...")
    GuiRecorderManager.state = GuiRecorderManager.States.COMPILING


    val compilationProcessBuilder = if (SystemInfo.isWindows)
      getProcessBuilderForWin(kotlinCompilerJar, libDirLocation, classpath, scriptKt)
    else
      ProcessBuilder("java", "-jar",
                     kotlinCompilerJar.path,
                     "-kotlin-home", libDirLocation.path,
                     "-d", tempDir.path,
                     "-cp", buildClasspath(classpath),
                     scriptKt.path)
    val process = compilationProcessBuilder.start()
    val wait = process.waitFor(120, TimeUnit.MINUTES)
    assert(wait)
    if (process.exitValue() == 1) {
      LOG.error(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n")))
      Notifier.updateStatus("Compilation error (see idea.log)")
      GuiRecorderManager.state = GuiRecorderManager.States.COMPILATION_ERROR
      throw CompilationException()
    }
    else {
      Notifier.updateStatus("Compilation is done")
      GuiRecorderManager.state = GuiRecorderManager.States.COMPILATION_DONE
    }
    return wait
  }

  private fun getKotlinCompilerJar(): File {
    val kotlinCompilerDir = getPluginKotlincDir()
    if (!isKotlinCompilerDir(kotlinCompilerDir)) downloadKotlinCompilerJar(kotlinCompilerDir.path)
    return kotlinCompilerDir.listFiles().firstOrNull { file -> file.name.contains("kotlin-compiler") }
           ?: throw FileNotFoundException("Unable to find kotlin-compiler*.jar in ${kotlinCompilerDir.path} directory")
  }

  private fun getApplicationLibDir(): File {
    return File(PathManager.getLibPath())
  }

  private fun getPluginKotlincDir(): File {
    val tempDirFile = File(PathManager.getTempPath())
    FileUtil.ensureExists(tempDirFile)
    return tempDirFile
  }

  private fun isKotlinCompilerDir(dir: File): Boolean = dir.listFiles().any { file ->
    file.name.contains("kotlin-compiler")
  }

  private fun downloadKotlinCompilerJar(destDirPath: String?): File {
    Notifier.updateStatus("${Notifier.LONG_OPERATION_PREFIX}Downloading kotlin-compiler.jar...")
    val downloader = DownloadableFileService.getInstance()
    val description = downloader.createFileDescription(kotlinCompilerJarUrl, kotlinCompilerJarName)
    ApplicationManager.getApplication().invokeAndWait(
      {
        downloader.createDownloader(Arrays.asList(description), kotlinCompilerJarName).downloadFilesWithProgress(destDirPath, null, null)
      })
    Notifier.updateStatus("kotlin-compiler.jar downloaded successfully")
    return File(destDirPath + File.separator + kotlinCompilerJarName)
  }

  private class CompilationException : Exception()


  private fun buildClasspath(cp: List<String>): String {
    if (SystemInfo.isWindows) {
      val ideaJar = "idea.jar"
      val ideaJarPath = cp.find { pathStr -> pathStr.endsWith("${File.separator}$ideaJar") }
      val ideaLibPath = ideaJarPath!!.substring(startIndex = 0, endIndex = ideaJarPath.length - ideaJar.length - File.separator.length)
      return cp.filterNot { pathStr -> pathStr.startsWith(ideaLibPath) }.plus(ideaLibPath).joinToString(";")
    }
    else return cp.joinToString(":")
  }

  private fun getProcessBuilderForWin(kotlinCompilerJar: File, libDirLocation: File, classpath: List<String>,
                                      scriptKt: File): ProcessBuilder {
    val moduleXmlFile = createTempFile(
      content = ModuleXmlBuilder.build(outputDir = tempDir.path, classPath = classpath, sourcePath = scriptKt.path), fileName = "module",
      extension = ".xml")
    return ProcessBuilder(
      "java", "-jar", kotlinCompilerJar.path, "-kotlin-home", libDirLocation.path, "-module", moduleXmlFile.path, scriptKt.path)
  }
}

