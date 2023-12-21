// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.be
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems

@TestApplication
class WSLDistributionTest {
  val distro = object : WSLDistribution("mock-wsl-id") {
    init {
      testOverriddenShellPath = "/bin/bash"
    }
  }

  val wslExe: String = FileSystems.getDefault().rootDirectories.first().resolve("mock-path").resolve("wsl.exe").toString()
  val toolsRoot = "/mnt/c/mock-path"

  @BeforeEach
  fun patchWslExePath(
    @TestDisposable disposable: Disposable,
  ) {
    WSLDistribution.testOverriddenWslExe(FileSystems.getDefault().getPath(wslExe), disposable)
    testOverrideWslToolRoot(toolsRoot, disposable)
  }

  @Nested
  inner class patchCommandLine {
    @Test
    fun `simple case`() {
      val cmd = distro.patchCommandLine(GeneralCommandLine("true"), null, WSLCommandLineOptions())
      assertSoftly(cmd) {
        exePath should be(wslExe)
        parametersList.list should be(listOf(
          "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c", "true",
        ))
        environment.entries should beEmpty()
      }
    }

    @Test
    fun `arguments and environment`() {
      val cmd = distro.patchCommandLine(
        GeneralCommandLine("printf", "foo", "bar", "'o\"ops 1'")
          .withEnvironment("FOOBAR", "'o\"ops 2'"),
        null,
        WSLCommandLineOptions(),
      )
      assertSoftly(cmd) {
        exePath should be(wslExe)
        parametersList.list should be(listOf(
          "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c",
          """export FOOBAR=''"'"'o"ops 2'"'"'' && printf foo bar ''"'"'o"ops 1'"'"''"""
        ))
        environment.entries should beEmpty()
      }
    }

    @Nested
    inner class `different WSLCommandLineOptions` {
      @Test
      fun `setExecuteCommandInShell false`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInShell should be(true)
            }
          }
          .setExecuteCommandInShell(false)

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "date",
          ))
          environment.entries should beEmpty()
        }
      }

      @Test
      fun `setExecuteCommandInShell false and environment variables`() {
        val commandLine = GeneralCommandLine("printenv")
          .withEnvironment("FOO", "BAR")
          .withEnvironment("HURR", "DURR")

        val options = WSLCommandLineOptions()
          .setExecuteCommandInShell(false)

        val cmd = distro.patchCommandLine(commandLine, null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "printenv",
          ))
          environment should be(mapOf(
            "FOO" to "BAR",
            "HURR" to "DURR",
            "WSLENV" to "FOO/u:HURR/u",
          ))
        }
      }

      @Test
      fun `setExecuteCommandInInteractiveShell true`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInInteractiveShell should be(false)
            }
          }
          .setExecuteCommandInInteractiveShell(true)

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-i", "-l", "-c", "date",
          ))
          environment.entries should beEmpty()
        }
      }

      @Test
      fun `setExecuteCommandInLoginShell false`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInLoginShell should be(true)
            }
          }
          .setExecuteCommandInLoginShell(false)

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-c", "date",
          ))
          environment.entries should beEmpty()
        }
      }

      @Test
      fun `setExecuteCommandInDefaultShell true`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInDefaultShell should be(false)
            }
          }
          .setExecuteCommandInDefaultShell(true)

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "$toolsRoot/ttyfix", "\$SHELL", "-c", "date",
          ))
          environment.entries should beEmpty()
        }
      }

      @Test
      fun `setSudo true`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isSudo should be(false)
            }
          }
          .setSudo(true)

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "-u", "root", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c", "date",
          ))
          environment.entries should beEmpty()
        }
      }
      @Test
      fun setRemoteWorkingDirectory() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              remoteWorkingDirectory should beNull()
            }
          }
          .setRemoteWorkingDirectory("/foo/bar/baz")

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c", "cd /foo/bar/baz && date",
          ))
          environment.entries should beEmpty()
        }
      }

      @Test
      fun `setPassEnvVarsUsingInterop true`() {
        val commandLine = GeneralCommandLine("date")
          .withEnvironment("FOO", "BAR")
          .withEnvironment("HURR", "DURR")

        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isPassEnvVarsUsingInterop should be(false)
            }
          }
          .setPassEnvVarsUsingInterop(true)

        val cmd = distro.patchCommandLine(commandLine, null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c", "date",
          ))
          environment should be(mapOf(
            "FOO" to "BAR",
            "HURR" to "DURR",
            "WSLENV" to "FOO/u:HURR/u",
          ))
        }
      }

      @Test
      fun addInitCommand() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              initShellCommands should haveSize(0)
            }
          }
          .addInitCommand("whoami")
          .addInitCommand("printf 'foo bar' && echo 'hurr durr'")  // Allows various shell injections.

        val cmd = distro.patchCommandLine(GeneralCommandLine("date"), null, options)
        assertSoftly(cmd) {
          exePath should be(wslExe)
          parametersList.list should be(listOf(
            "--distribution", "mock-wsl-id", "--exec", "$toolsRoot/ttyfix", "/bin/bash", "-l", "-c",
            "printf 'foo bar' && echo 'hurr durr' && whoami && date",
          ))
          environment.entries should beEmpty()
        }
      }
    }
  }
}