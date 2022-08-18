// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import com.google.gson.Gson
import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ModernApplicationStarter
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

data class ParameterValue(
  val parameterName: String,
  val parameterValue: String
)

data class LaunchParameters(
  val groupOfParametersName: String,
  val parameters: MutableList<ParameterValue>
)

internal class DumpLaunchParametersStarter : ModernApplicationStarter() {
  override val commandName: String
    get() = "dump-launch-parameters"

  override fun premain(args: List<String>) {
    val argsMap = args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
      if (elem.startsWith("-")) Pair(map + (elem to emptyList()), elem)
      else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first

    if (!argsMap.containsKey("-f") || argsMap["-f"]!!.isEmpty()) {
      exitProcess(AppExitCodes.STARTUP_EXCEPTION)
    }

    val outputFile = argsMap["-f"]!![0]

    val launchParametersDump = File(outputFile)
    val launchParameters = mutableListOf<LaunchParameters>()

    launchParameters.add(getVmOptions())
    launchParameters.add(getCommandLineArguments(args))

    if (argsMap.containsKey("-p") && !argsMap["-p"]!!.isEmpty()) {
      val javaSystemProperties: MutableList<ParameterValue> = mutableListOf()
      argsMap["-p"]?.forEach { javaSystemProperties.add(getJavaSystemProperty(it)) }
      launchParameters.add(LaunchParameters("javaSystemProperties", javaSystemProperties))
    }

    if (argsMap.containsKey("-e") && !argsMap["-e"]!!.isEmpty()) {
      val environmentVariables: MutableList<ParameterValue> = mutableListOf()
      argsMap["-e"]?.forEach { environmentVariables.add(getEnvironmentVariable(it)) }
      launchParameters.add(LaunchParameters("environmentVariables", environmentVariables))
    }

    launchParametersDump.appendText(Gson().toJson(launchParameters))
    exitProcess(0)
  }

  override suspend fun start(args: List<String>) {
    exitProcess(0)
  }

  private fun getVmOptions(): LaunchParameters {
    val vmOptionsFromJava = ManagementFactory.getRuntimeMXBean().inputArguments
    val vmOptionsParameters: MutableList<ParameterValue> = mutableListOf()
    vmOptionsFromJava.forEachIndexed { i, vmOption ->
      vmOptionsParameters.add(ParameterValue("vmOption.$i", vmOption))
    }
    return LaunchParameters("vmOptions", vmOptionsParameters)
  }

  private fun getCommandLineArguments(args: List<String>): LaunchParameters {
    val arguments: MutableList<ParameterValue> = mutableListOf()
    args.forEachIndexed { i, argument ->
      arguments.add(ParameterValue("argument.$i", argument))
    }
    return LaunchParameters("arguments", arguments)
  }

  private fun getJavaSystemProperty(property: String): ParameterValue {
    return ParameterValue(property, System.getProperty(property))
  }

  private fun getEnvironmentVariable(envVariable: String): ParameterValue {
    return ParameterValue(envVariable, System.getenv(envVariable))
  }
}