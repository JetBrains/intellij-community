// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleInitScriptUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.tooling.internal.init.Init
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher


fun createMainInitScript(isBuildSrcProject: Boolean, toolingExtensionClasses: Set<Class<*>>): File {
  val jarPaths = GradleExecutionHelper.getToolingExtensionsJarPaths(toolingExtensionClasses)
  return createInitScript("ijInit", loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/Init.gradle", mapOf(
    "EXTENSIONS_JARS_PATH" to jarPaths.toGroovyList { "mapPath(" + toGroovyString() + ")" },
    "IS_BUILD_SCR_PROJECT" to isBuildSrcProject.toString()
  )))
}

fun createTestInitScript(testPatterns: Set<String>, forceExecution: Boolean): File {
  return createInitScript("ijTestInit", loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/testFilterInit.gradle", mapOf(
    "TEST_PATTERNS" to testPatterns.toGradleGroovyList { toGradleGroovyString() },
    "FORCE_TEST_EXECUTION" to forceExecution.toGradleGroovyBoolean()
  )))
}

private fun loadInitScript(resourcePath: String, parameters: Map<String, String>): String {
  var script = loadInitScript(resourcePath)
  for ((key, value) in parameters) {
    val replacement = Matcher.quoteReplacement(value)
    script = script.replaceFirst(key.toRegex(), replacement)
  }
  return script
}

private fun loadInitScript(resourcePath: String): String {
  val resource = Init::class.java.getResource(resourcePath)
  if (resource == null) {
    throw IllegalArgumentException("Cannot find init file $resourcePath")
  }
  try {
    return resource.readText()
  }
  catch (e: IOException) {
    throw IllegalStateException("Cannot read init file $resourcePath", e)
  }
}

fun createInitScript(prefix: String, content: String): File {
  val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
  val contentLength = contentBytes.size
  return FileUtil.findSequentFile(File(FileUtil.getTempDirectory()), prefix, GradleConstants.EXTENSION) { file: File ->
    try {
      if (!file.exists()) {
        FileUtil.writeToFile(file, contentBytes, false)
        @Suppress("SSBasedInspection")
        file.deleteOnExit()
        return@findSequentFile true
      }
      if (contentLength.toLong() != file.length()) return@findSequentFile false
      return@findSequentFile content == FileUtil.loadFile(file, StandardCharsets.UTF_8)
    }
    catch (ignore: IOException) {
      // Skip file with access issues. Will attempt to check the next file
    }
    false
  }
}
