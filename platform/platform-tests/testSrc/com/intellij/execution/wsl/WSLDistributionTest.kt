// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.containers.orNull
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.be
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import org.junit.AssumptionViolatedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.*
import java.nio.file.FileSystems
import java.util.stream.Stream

@TestApplication
@ExtendWith(WslTestStrategyExtension::class)
class WSLDistributionTest {
  @BeforeEach
  fun patchWslExePath(
    @TestDisposable disposable: Disposable,
  ) {
    WSLDistribution.testOverriddenWslExe(FileSystems.getDefault().getPath(wslExe), disposable)
    testOverrideWslToolRoot(toolsRoot, disposable)
  }

  @Nested
  inner class patchCommandLine {
    @TestTemplate
    fun `simple case`(strategy: WslTestStrategy) {
      val cmd = strategy.patch(GeneralCommandLine("true"), WSLCommandLineOptions())
      assertSoftly(cmd) {
        argv should be(strategy.argv(
          wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
          cmd = listOf(TEST_SHELL, "-l", "-c", "true"),
        ))
        environment.entries should beEmpty()
      }
    }

    @TestTemplate
    fun `arguments and environment`(strategy: WslTestStrategy) {
      val options = WSLCommandLineOptions()
      withClue("Checking the default value for an option. If it fails, the test should be revised") {
        options.isPassEnvVarsUsingInterop should be(false)
      }
      val cmd = strategy.patch(
        GeneralCommandLine("printf", "foo", "bar", "'o\"ops 1'")
          .withEnvironment("FOOBAR", "'o\"ops 2'")
          .withEnvironment("HURR", "DURR"),
        options,
      )
      assertSoftly(cmd) {
        argv should be(strategy.argv(
          wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
          cmd = listOf(
            TEST_SHELL, "-l", "-c",
            """export FOOBAR=''"'"'o"ops 2'"'"'' && export HURR=DURR && printf foo bar ''"'"'o"ops 1'"'"''""",
          ),
        ))
        environment.entries should beEmpty()
      }
    }

    @Nested
    inner class `different WSLCommandLineOptions` {
      @TestTemplate
      fun `setExecuteCommandInShell false`(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInShell should be(true)
            }
          }
          .setExecuteCommandInShell(false)

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec"),
            cmd = listOf("date")
          ))
          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun `setExecuteCommandInShell false and environment variables`(strategy: WslTestStrategy) {
        val commandLine = GeneralCommandLine("printenv")
          .withEnvironment("FOO", "BAR")
          .withEnvironment("HURR", "DURR")

        val options = WSLCommandLineOptions()
          .setExecuteCommandInShell(false)

        val cmd = strategy.patch(commandLine, options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec"),
            cmd = listOf("printenv")
          ))
          environment should be(strategy.environment(
            "FOO" to "BAR",
            "HURR" to "DURR",
          ))
        }
      }

      @TestTemplate
      fun `setExecuteCommandInInteractiveShell true`(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInInteractiveShell should be(false)
            }
          }
          .setExecuteCommandInInteractiveShell(true)

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-i", "-l", "-c", "date"),
          ))
          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun `setExecuteCommandInLoginShell false`(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInLoginShell should be(true)
            }
          }
          .setExecuteCommandInLoginShell(false)

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-c", "date"),
          ))
          environment.entries should beEmpty()
        }
      }

      /** This test makes sense only for the legacy strategy. */
      @Test
      fun `setExecuteCommandInDefaultShell true`() {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isExecuteCommandInDefaultShell should be(false)
            }
          }
          .setExecuteCommandInDefaultShell(true)

        val cmd = WslTestStrategy.Legacy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(listOf(
            wslExe, "--distribution", WSL_ID, "$toolsRoot/ttyfix", "\$SHELL", "-c", "date",
          ))
          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun `setSudo true`(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              isSudo should be(false)
            }
          }
          .setSudo(true)

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "-u", "root", "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-l", "-c", "date"),
          ))

          when (strategy) {
            WslTestStrategy.Legacy -> Unit
            WslTestStrategy.Ijent -> throw AssumptionViolatedException("not implemented yet")
          }

          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun setRemoteWorkingDirectory(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              remoteWorkingDirectory should beNull()
            }
          }
          .setRemoteWorkingDirectory("/foo/bar/baz")

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-l", "-c", "cd /foo/bar/baz && date"),
          ))
          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun `setPassEnvVarsUsingInterop true`(strategy: WslTestStrategy) {
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

        val cmd = strategy.patch(commandLine, options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-l", "-c", "date"),
          ))
          environment should be(strategy.environment(
            "FOO" to "BAR",
            "HURR" to "DURR",
          ))
        }
      }

      @TestTemplate
      fun addInitCommand(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              initShellCommands should haveSize(0)
            }
          }
          .addInitCommand("whoami")
          .addInitCommand("printf 'foo bar' && echo 'hurr durr'")  // Allows various shell injections.

        val cmd = strategy.patch(GeneralCommandLine("date"), options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(TEST_SHELL, "-l", "-c", "printf 'foo bar' && echo 'hurr durr' && whoami && date"),
          ))
          environment.entries should beEmpty()
        }
      }

      @TestTemplate
      fun `addInitCommand and cwd and env`(strategy: WslTestStrategy) {
        val options = WSLCommandLineOptions()
          .apply {
            withClue("Checking the default value") {
              initShellCommands should haveSize(0)
            }
          }
          .addInitCommand("whoami")
          .addInitCommand("pwd")
          .setRemoteWorkingDirectory("/foo/bar")
          .setPassEnvVarsUsingInterop(false)

        val sourceCmd = GeneralCommandLine("date")
          .withEnvironment("HURR", "DURR")
          .withEnvironment("HERP", "DERP")

        val cmd = strategy.patch(sourceCmd, options)
        assertSoftly(cmd) {
          argv should be(strategy.argv(
            wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
            cmd = listOf(
              TEST_SHELL, "-l", "-c",
              // The test stuck to the already existed implementation of WSLDistribution, however it worked.
              "pwd && whoami && export HERP=DERP && export HURR=DURR && cd /foo/bar && date",
            ),
          ))
          environment.entries should beEmpty()
        }
      }
    }
  }

  private val GeneralCommandLine.argv: List<String>
    get() = listOf(exePath) + parametersList.list

  private fun WslTestStrategy.argv(wslExeParams: List<String>, cmd: List<String>): List<String> = when (this) {
    WslTestStrategy.Legacy -> wslExeParams + cmd
    WslTestStrategy.Ijent -> cmd
  }

  private fun WslTestStrategy.environment(vararg entries: Pair<String, String>) = when (this) {
    WslTestStrategy.Legacy ->
      mapOf(
        *entries,
        "WSLENV" to entries.sortedBy { (name, _) -> name }.joinToString(":") { (name, _) -> "$name/u" },
      )

    WslTestStrategy.Ijent -> mapOf(*entries)
  }

  private fun WslTestStrategy.patch(cmd: GeneralCommandLine, options: WSLCommandLineOptions): GeneralCommandLine {
    val mockWslDistribution = object : WSLDistribution(WSL_ID) {
      init {
        testOverriddenShellPath = TEST_SHELL
      }
    }

    return when (this) {
      WslTestStrategy.Legacy -> mockWslDistribution.patchCommandLine(cmd, null, options)
      WslTestStrategy.Ijent -> throw AssumptionViolatedException("Not implemented yet")
    }
  }
}

enum class WslTestStrategy { Legacy, Ijent }

private class WslTestStrategyExtension
  : TestTemplateInvocationContextProvider,
    TestInstancePreConstructCallback,
    TestInstancePreDestroyCallback {

  override fun supportsTestTemplate(extension: ExtensionContext): Boolean =
    extension.testMethod.orNull()
      ?.parameters
      ?.map { it.type }
      ?.any(WslTestStrategy::class.java::isAssignableFrom)
    ?: false

  @Suppress("USELESS_CAST", "SSBasedInspection")
  override fun provideTestTemplateInvocationContexts(extension: ExtensionContext): Stream<TestTemplateInvocationContext> =
    WslTestStrategy.entries.map { MyTestTemplateInvocationContext(it) as TestTemplateInvocationContext }.stream()

  override fun preDestroyTestInstance(extensionContext: ExtensionContext) {
    val value = when (extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(WslTestStrategy::class.java) as WslTestStrategy?) {
      null -> false
      WslTestStrategy.Legacy -> false
      WslTestStrategy.Ijent -> true
    }
    Registry.get("wsl.use.remote.agent.for.launch.processes").setValue(value)
  }

  override fun preConstructTestInstance(testInstanceFactoryContext: TestInstanceFactoryContext, extension: ExtensionContext) {
    Registry.get("wsl.use.remote.agent.for.launch.processes").resetToDefault()
  }

  private class MyTestTemplateInvocationContext(
    private val strategy: WslTestStrategy,
  ) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String = strategy.name

    override fun getAdditionalExtensions(): List<Extension> =
      listOf(object : ParameterResolver {
        override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
          WslTestStrategy::class.java.isAssignableFrom(parameterContext.parameter.type)

        override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
          extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).put(WslTestStrategy::class.java, strategy)
          return strategy
        }
      })
  }
}

private val wslExe: String by lazy {
  FileSystems.getDefault().rootDirectories.first().resolve("mock-path").resolve("wsl.exe").toString()
}

private const val toolsRoot = "/mnt/c/mock-path"
private const val TEST_SHELL = "/bin/bash"
private const val WSL_ID = "mock-wsl-id"
