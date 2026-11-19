// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties

/**
 * The command line and the arg file lines of a local run.
 */
internal class ArgFileCommandLine(val commandLine: List<String>, val argFileLines: List<String>)

internal object ArgFileCommandLines {
  /**
   * Returns the parameters of a module run that puts the VM options and the main class in an arg file.
   */
  @JvmStatic
  fun argFileParameters(): SimpleJavaParameters = SimpleJavaParameters().apply {
    jdk = SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome())
    modulePath.add("/classes/hello.jar")
    moduleName = "hello"
    mainClass = "hello.Main"
    isArgFile = true
    setUseDynamicClasspath(true)
    setUseDynamicVMOptions(true)
    setUseDynamicParameters(true)
  }

  /**
   * Builds the command line of [parameters] for a local target and reads the arg file.
   * Deletes the temporary files of the command line.
   */
  @JvmStatic
  fun build(parameters: SimpleJavaParameters): ArgFileCommandLine {
    val request = LocalTargetEnvironmentRequest()
    val builder = parameters.toCommandLine(request)
    request.prepareEnvironment(TargetProgressIndicator.EMPTY)
    val files = builder.filesToDeleteOnTermination
    try {
      val commandLine = builder.build().collectCommandsSynchronously()
      val argFileLines = files.single { it.name.startsWith("idea_arg_file") }.readLines()
      return ArgFileCommandLine(commandLine, argFileLines)
    }
    finally {
      files.forEach(FileUtil::delete)
    }
  }
}
