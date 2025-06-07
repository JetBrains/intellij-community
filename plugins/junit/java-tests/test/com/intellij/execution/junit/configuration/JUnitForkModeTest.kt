// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.configuration

import com.intellij.openapi.util.io.FileUtil
import com.intellij.rt.execution.testFrameworks.ProcessBuilder
import com.intellij.rt.junit.JUnitForkedSplitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class JUnitForkModeTest {
  @Test
  fun `should quote args file`() {
    var processBuilder: StubProcessBuilder? = null
    val parentProcessClasspath = "strange classpath\""
    object : JUnitForkedSplitter<String>(
      "", "method", listOf("arg3", "arg4")
    ) {

      override fun initProcessBuilder(): ProcessBuilder? {
        assertNull(processBuilder, "initProcessBuilder is called more than once")
        processBuilder = createStubProcessBuilder()
        return processBuilder
      }

      override fun startSplitting(args: Array<out String>, configName: String?, commandLinePath: String?, repeatCount: String?): Int {
        myVMParameters = listOf<String>()
        myDynamicClasspath = "ARGS_FILE"

        return splitChildren(getChildren(null), 0, false, null, parentProcessClasspath, emptyList(), repeatCount)
      }

      override fun splitChildren(children: List<String>, result: Int, forkTillMethod: Boolean, workingDir: File?, classpath: String, moduleOptions: List<String?>?, repeatCount: String?): Int {
        for (child in children) {
          startChildFork(listOf(child), workingDir, classpath, emptyList(), repeatCount)
        }
        return 0
      }

      override fun getChildren(child: String?): List<String> {
        return listOf("child")
      }
    }.startSplitting(emptyArray<String>(), null, null, null)

    assertNotNull(processBuilder, "initProcessBuilder is not called")
    val parametersList = processBuilder.getParametersList()
    val classPathIndex = parametersList.indexOf("-classpath")
    assertTrue(classPathIndex != -1) { "classpath option should be present" }
    val argsFile = parametersList.getOrNull(classPathIndex + 1)
    assertNotNull(argsFile, "args file should be presented")
    assertTrue(argsFile.startsWith("@")) { "invalid args file: $argsFile" }
    val actualClassPath = FileUtil.loadFile(File(argsFile.substring(1)))
    assertEquals("strange\" \"classpath\"\\\"\"", actualClassPath)
  }


  private fun createStubProcessBuilder(): StubProcessBuilder {
    return StubProcessBuilder()
  }

  class StubProcessBuilder : ProcessBuilder() {
    fun getParametersList(): List<String> {
      return myParameters
    }

    override fun createProcess(): Process? {
      return object : Process() {
        override fun getOutputStream(): OutputStream? = null

        override fun getInputStream(): InputStream? = InputStream.nullInputStream()

        override fun getErrorStream(): InputStream? = InputStream.nullInputStream()

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
      }
    }
  }
}

