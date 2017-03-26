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
package com.intellij.testGuiFramework.tests

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.vcs.ExecutableHelper
import git4idea.config.GitVcsApplicationSettings
import java.util.*
import java.util.regex.Pattern

object GitSettings{

  fun setup() {
    setPathToGit()
    cache()
    setTestSettings()
  }

  fun restore() {
    if (gitGlobalUserNameCached != null) setGitGlobalUserName(gitGlobalUserNameCached!!)
    if (gitGlobalUserEmailCached != null) setGitGlobalUserEmail(gitGlobalUserEmailCached!!)
  }

  private val TEST_GIT_USER_NAME = "jetbrains_tester";
  private val TEST_GIT_USER_EMAIL = "test@jetbrains.com";

  private var gitGlobalUserNameCached: String? = null
  private var gitGlobalUserEmailCached: String? = null

  private fun setTestSettings() {
    setGitGlobalUserName(TEST_GIT_USER_NAME)
    setGitGlobalUserEmail(TEST_GIT_USER_EMAIL)
  }

  private fun cache() {
    cacheGitGlobalUserName()
    cacheGitGlobalUserEmail()
  }

  private fun cacheGitGlobalUserName() { gitGlobalUserNameCached = executeGitCommand("config user.name") }
  private fun cacheGitGlobalUserEmail() { gitGlobalUserEmailCached = executeGitCommand("config user.email")
  }

  private fun setGitGlobalUserName(userName: String) { executeGitCommand("config --global user.name \"$userName\"") }
  private fun setGitGlobalUserEmail(userEmail: String) { executeGitCommand("config --global user.email $userEmail") }

  private fun setPathToGit() { GitVcsApplicationSettings.getInstance().setPathToGit(ExecutableHelper.findGitExecutable()) }

  private fun getPathToGit() = GitVcsApplicationSettings.getInstance().pathToGit

  private fun executeGitCommand(commandLine: String) = executeGitCommand(commandLine.splitSpaceIgnoreQuotes())

  private fun executeGitCommand(params: List<String>): String? {
    val myCommandLine = GeneralCommandLine()

    myCommandLine.exePath = getPathToGit()
    myCommandLine.addParameters(params)
    val clientProcess = myCommandLine.createProcess()

    val commandLineParams = StringUtil.join(params, " ")
    val handler = CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset(), commandLineParams)
    val result = handler.runProcess(30 * 1000)
    if (result.isTimeout) {
      throw RuntimeException("Timeout waiting for the command execution. Command: " + commandLineParams)
    }

    return result.stdout.removeSuffix("\n")
  }

  private fun String.splitSpaceIgnoreQuotes(): List<String> {
    val matchList = ArrayList<String>()
    val regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")
    val regexMatcher = regex.matcher(this)
    while (regexMatcher.find()) {
      if (regexMatcher.group(1) != null) {
        // Add double-quoted string without the quotes
        matchList.add(regexMatcher.group(1))
      }
      else if (regexMatcher.group(2) != null) {
        // Add single-quoted string without the quotes
        matchList.add(regexMatcher.group(2))
      } else {
        // Add unquoted word
        matchList.add(regexMatcher.group())
      }
    }
    return matchList
  }
}