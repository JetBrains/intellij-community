// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.use
import com.intellij.platform.ijent.*
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.orNull
import com.intellij.util.io.Ksuid
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.be
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.*
import java.io.File
import java.nio.file.FileSystems
import java.util.stream.Stream
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * This unit test is supposed to ensure that there are no sudden changes in the behaviour of [WSLDistribution.patchCommandLine],
 * and that the adapter [WslIjentManager] behaves the same way as the original [WSLDistribution].
 */
@TestApplication
@ExtendWith(WslTestStrategyExtension::class)
class WSLDistributionTest {
  @BeforeEach
  fun patchWslExePath(
    @TestDisposable disposable: Disposable,
  ) {
    WSLDistribution.testOverriddenWslExe(FileSystems.getDefault().getPath(wslExe), disposable)
    testOverrideWslToolRoot(toolsRoot, disposable)

    val oldWslPathIsSystemCompatible = WSLUtil.isSystemCompatible()
    Disposer.register(disposable) {
      WSLUtil.setSystemCompatible(oldWslPathIsSystemCompatible)
    }
    WSLUtil.setSystemCompatible(true)
  }

  @TestTemplate
  fun `no sudden changes in WSLCommandLineOptions`(strategy: WslTestStrategy) {
    val options = WSLCommandLineOptions()
    val defaultValues = WSLCommandLineOptions::class.memberProperties
      .map { property ->
        property.isAccessible = true
        "${property.name} = ${property.get(options)}"
      }
      .sorted()
      .joinToString("\n")

    val launchWithWslExe = when (strategy) {
      WslTestStrategy.Legacy -> true
      WslTestStrategy.Ijent -> false
    }

    withClue("""
      Changes in WSLCommandLineOptions should be performed cautiously. 
      There is WslIjentManager that tries to behave the same way as WSLDistribution.patchCommandLine.
      If any new logic is added to WSLDistribution, the corresponding logic should be implemented in WslIjentManager.
      Please, update this test after doing all the necessary changes to fix the current state of WSLCommandLineOptions.
    """.trimIndent()) {
      defaultValues should be("""
        myExecuteCommandInDefaultShell = false
        myExecuteCommandInInteractiveShell = false
        myExecuteCommandInLoginShell = true
        myExecuteCommandInShell = true
        myInitShellCommands = []
        myLaunchWithWslExe = $launchWithWslExe
        myPassEnvVarsUsingInterop = false
        myRemoteWorkingDirectory = null
        mySleepTimeoutSec = 0.0
        mySudo = false
      """.trimIndent())
    }
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

    /** IDEA-351354 */
    @TestTemplate
    fun `environment variables with brackets`(strategy: WslTestStrategy) {
      val options = WSLCommandLineOptions()
      withClue("Checking the default value for an option. If it fails, the test should be revised") {
        options.isPassEnvVarsUsingInterop should be(false)
      }
      val cmd = strategy.patch(
        GeneralCommandLine("true")
          .withEnvironment("CommonProgramFiles", "/mnt/c/Program Files/Common Files")
          .withEnvironment("CommonProgramFiles(x86)", "/mnt/c/Program Files (x86)/Common Files")
          .withEnvironment("ProgramFiles", "/mnt/c/Program Files")
          .withEnvironment("Path", "/mnt/c/ProgramData/chocolatey/bin;C:/WINDOWS/system32;C:/WINDOWS;C:/Program Files (x86)/Gpg4win/../GnuPG/bin")
          .withEnvironment("ProgramFiles(x86)", "/mnt/c/Program Files (x86)"),
        options,
      )
      assertSoftly(cmd) {
        argv should be(strategy.argv(
          wslExeParams = listOf(wslExe, "--distribution", WSL_ID, "--exec", "$toolsRoot/ttyfix"),
          cmd = listOf(
            TEST_SHELL, "-l", "-c",
            // It doesn't matter if Windows paths should be passed into environment variables for running something on Linux.
            // The goal of this test is to ensure that both filters return the same output.
            "export CommonProgramFiles='/mnt/c/Program Files/Common Files'" +
            " && export Path='/mnt/c/ProgramData/chocolatey/bin;C:/WINDOWS/system32;C:/WINDOWS;C:/Program Files (x86)/Gpg4win/../GnuPG/bin'" +
            " && export ProgramFiles='/mnt/c/Program Files'" +
            " && true",
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
            WslTestStrategy.Ijent -> {
              cmd.getUserData(TEST_ROOT_USER_SET) should be(true)
            }
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

  @Nested
  inner class `GeneralCommandLine and IJent` {
    @Test
    fun `workingDirectory is preserved`() {
      val options = WSLCommandLineOptions()

      val sourceCmd = GeneralCommandLine("true")
        .withWorkDirectory("""\\wsl.localhost\$WSL_ID\foo\bar""")

      val cmd = WslTestStrategy.Ijent.patch(sourceCmd, options)

      cmd.workDirectory should be(File("/foo/bar"))
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

    return ProgressManager.getInstance().runProcess(
      Computable {
        when (this) {
          WslTestStrategy.Legacy -> mockWslDistribution.patchCommandLine(cmd, null, options)
          WslTestStrategy.Ijent -> passGeneralCommandLineThroughWslIjentManager(mockWslDistribution, cmd, options)
        }
      },
      EmptyProgressIndicator()  // These particular tests don't require any really cancellable progress indicator.
    )
  }

  private fun passGeneralCommandLineThroughWslIjentManager(
    mockWslDistribution: WSLDistribution,
    sourceCommandLine: GeneralCommandLine,
    options: WSLCommandLineOptions,
  ): GeneralCommandLine =
    Disposer.newDisposable().use { disposable ->
      @Suppress("SSBasedInspection") val scope = CoroutineScope(CoroutineName("A mock scope that should not be actually used"))
      Disposer.register(disposable) { scope.cancel() }

      val adapter = GeneralCommandLine()

      ApplicationManager.getApplication().replaceService(
        WslIjentManager::class.java,
        object : WslIjentManager {
          @DelicateCoroutinesApi
          override val processAdapterScope: CoroutineScope = scope

          override suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi {
            require(wslDistribution == mockWslDistribution) { "$wslDistribution != $mockWslDistribution" }
            return MockIjentApi(adapter, rootUser)
          }

          override val isIjentAvailable: Boolean = true
        },
        disposable,
      )

      options.isLaunchWithWslExe = false  // Exploiting the knowledge about internals of WSLDistribution.mustRunCommandLineWithIjent
      mockWslDistribution.patchCommandLine(sourceCommandLine, null, options)
      withClue("WslIjentManager substitutes setProcessCreator") {
        sourceCommandLine.isProcessCreatorSet should be(true)
      }

      withClue("Checking that the mock works") {
        val err = shouldThrow<ProcessNotCreatedException> {
          sourceCommandLine.createProcess()
        }
        err.message should be(executeResultMock.message)
      }
      adapter
    }
}

enum class WslTestStrategy { Legacy, Ijent }

private class MockIjentApi(private val adapter: GeneralCommandLine, val rootUser: Boolean) : IjentPosixApi {
  override val platform: IjentPlatform get() = throw UnsupportedOperationException()

  override val isRunning: Boolean get() = true

  override val info: IjentPosixInfo get() = throw UnsupportedOperationException()

  override fun close(): Unit = Unit

  override suspend fun waitUntilExit(): Unit = Unit

  override val exec: IjentExecApi get() = MockIjentExecApi(adapter, rootUser)

  override val fs: IjentFileSystemPosixApi get() = throw UnsupportedOperationException()

  override val tunnels: IjentTunnelsPosixApi get() = throw UnsupportedOperationException()
}

private class MockIjentExecApi(private val adapter: GeneralCommandLine, private val rootUser: Boolean) : IjentExecApi {
  override fun executeProcessBuilder(exe: String): IjentExecApi.ExecuteProcessBuilder =
    MockIjentApiExecuteProcessBuilder(adapter.apply { exePath = exe }, rootUser)

  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = mapOf("SHELL" to TEST_SHELL)
}

private val TEST_ROOT_USER_SET by lazy { Key.create<Boolean>("TEST_ROOT_USER_SET") }

private class MockIjentApiExecuteProcessBuilder(
  private val adapter: GeneralCommandLine,
  rootUser: Boolean,
) : IjentExecApi.ExecuteProcessBuilder {
  init {
    if (rootUser) {
      adapter.putUserData(TEST_ROOT_USER_SET, true)
    }
  }

  override fun args(args: List<String>): IjentExecApi.ExecuteProcessBuilder = apply {
    adapter.parametersList.run {
      clearAll()
      addAll(args)
    }
  }

  override fun env(env: Map<String, String>): IjentExecApi.ExecuteProcessBuilder = apply {
    adapter.environment.run {
      clear()
      putAll(env)
    }
  }

  override fun pty(pty: IjentExecApi.Pty?): IjentExecApi.ExecuteProcessBuilder = this

  override fun workingDirectory(workingDirectory: String?): IjentExecApi.ExecuteProcessBuilder = apply {
    adapter.setWorkDirectory(workingDirectory)
  }

  override suspend fun execute(): IjentExecApi.ExecuteProcessResult = executeResultMock
}

private val executeResultMock by lazy {
  IjentExecApi.ExecuteProcessResult.Failure(errno = 12345, message = "mock result ${Ksuid.generate()}")
}

private class WslTestStrategyExtension
  : TestTemplateInvocationContextProvider,
    AfterEachCallback,
    BeforeEachCallback {

  override fun supportsTestTemplate(extension: ExtensionContext): Boolean =
    extension.testMethod.orNull()
      ?.parameters
      ?.map { it.type }
      ?.any(WslTestStrategy::class.java::isAssignableFrom)
    ?: false

  @Suppress("USELESS_CAST", "SSBasedInspection")
  override fun provideTestTemplateInvocationContexts(extension: ExtensionContext): Stream<TestTemplateInvocationContext> =
    WslTestStrategy.entries.map { MyTestTemplateInvocationContext(it) as TestTemplateInvocationContext }.stream()

  override fun beforeEach(context: ExtensionContext) {
    val disposable = Disposer.newDisposable()
    context.getStore(ExtensionContext.Namespace.GLOBAL).put(this to Disposable::class.java, disposable)

    val oldService = WslIjentAvailabilityService.getInstance()
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      WslIjentAvailabilityService::class.java,
      object : WslIjentAvailabilityService by oldService {
        override fun runWslCommandsViaIjent(): Boolean =
          when (context.getStore(ExtensionContext.Namespace.GLOBAL).get(WslTestStrategy::class.java) as WslTestStrategy?) {
            null -> false
            WslTestStrategy.Legacy -> false
            WslTestStrategy.Ijent -> true
          }
      },
      disposable,
    )
  }

  override fun afterEach(context: ExtensionContext) {
    Disposer.dispose(context.getStore(ExtensionContext.Namespace.GLOBAL).get(this to Disposable::class.java) as Disposable)
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
