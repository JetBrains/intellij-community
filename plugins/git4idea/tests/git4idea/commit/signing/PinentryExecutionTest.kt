// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.util.io.createDirectories
import git4idea.commit.signing.GpgAgentConfigurator.Companion.GPG_AGENT_DEFAULT_CACHE_TTL_CONF_KEY
import git4idea.commit.signing.GpgAgentConfigurator.Companion.GPG_AGENT_DEFAULT_CACHE_TTL_CONF_VALUE
import git4idea.commit.signing.GpgAgentConfigurator.Companion.GPG_AGENT_MAX_CACHE_TTL_CONF_KEY
import git4idea.commit.signing.GpgAgentConfigurator.Companion.GPG_AGENT_MAX_CACHE_TTL_CONF_VALUE
import git4idea.commit.signing.GpgAgentConfigurator.Companion.GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_BACKUP_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_HOME_DIR
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.PINENTRY_LAUNCHER_FILE_NAME
import git4idea.config.GitExecutableManager
import git4idea.repo.GitProjectConfigurationCache
import git4idea.test.GitSingleRepoTest
import org.junit.Assume.assumeTrue
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.BindException
import java.nio.charset.StandardCharsets
import kotlin.io.path.readLines
import kotlin.random.Random

class PinentryExecutionTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()
    git("config commit.gpgSign true")
    git("config user.signingkey 0A46826A!")
    GitProjectConfigurationCache.getInstance(project).clearCache()
    val enabled = GpgAgentConfigurator.isEnabled(project, GitExecutableManager.getInstance().getExecutable(project))
    assumeTrue("GpgAgentConfigurator should be enabled", enabled);
  }

  fun `test pinentry communication without gpg agent configuration`() {
    IoTestUtil.assumeUnix()

    val pathLocator = TestGpgPathLocator()
    val paths = pathLocator.resolvePaths()!!
    project.service<GpgAgentConfigurator>().doConfigure(pathLocator)

    requestPasswordAndAssert(paths)
  }

  fun `test pinentry communication with existing gpg agent configuration`() {
    IoTestUtil.assumeUnix()

    val pathLocator = TestGpgPathLocator()
    val paths = pathLocator.resolvePaths()!!
    FileUtil.writeToFile(paths.gpgAgentConf.toFile(), "$GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY /usr/local/bin/pinentry")
    project.service<GpgAgentConfigurator>().doConfigure(pathLocator)
    val generatedConfig = paths.gpgAgentConf.readLines()
    assertTrue(generatedConfig.size == 3)
    assertTrue(generatedConfig[0] == "$GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY ${paths.gpgPinentryAppLauncherConfigPath}")

    requestPasswordAndAssert(paths)
  }

  fun `test existing gpg agent configuration but without pinentry program specified`() {
    IoTestUtil.assumeUnix()

    val pathLocator = TestGpgPathLocator()
    val paths = pathLocator.resolvePaths()!!
    val allowLoopbackPinentryConfig = "allow-loopback-pinentry"
    val defaultCacheTtlConfig = "$GPG_AGENT_DEFAULT_CACHE_TTL_CONF_KEY $GPG_AGENT_DEFAULT_CACHE_TTL_CONF_VALUE"
    val maxCacheTtlConfig = "$GPG_AGENT_MAX_CACHE_TTL_CONF_KEY $GPG_AGENT_MAX_CACHE_TTL_CONF_VALUE"
    val pinentryConfig = "$GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY ${paths.gpgPinentryAppLauncherConfigPath}"

    FileUtil.writeToFile(paths.gpgAgentConf.toFile(), "$allowLoopbackPinentryConfig\n$defaultCacheTtlConfig\n$maxCacheTtlConfig")
    project.service<GpgAgentConfigurator>().doConfigure(pathLocator)
    val generatedConfig = paths.gpgAgentConf.readLines()

    assertContainsOrdered(generatedConfig, listOf(allowLoopbackPinentryConfig, defaultCacheTtlConfig, maxCacheTtlConfig, pinentryConfig))
  }

  fun `test pinentry launcher structure`() {
    val pathLocator = TestGpgPathLocator()
    val paths = pathLocator.resolvePaths()!!

    project.service<GpgAgentConfigurator>().doConfigure(pathLocator)
    var scriptContent = FileUtil.loadFile(paths.gpgPinentryAppLauncher.toFile())
    assertScriptContentStructure(scriptContent)

    FileUtil.delete(paths.gpgPinentryAppLauncher.toFile())
    FileUtil.delete(paths.gpgAgentConf.toFile())
    FileUtil.delete(paths.gpgAgentConfBackup.toFile())

    FileUtil.writeToFile(paths.gpgAgentConf.toFile(), "$GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY /usr/local/bin/pinentry")
    project.service<GpgAgentConfigurator>().doConfigure(pathLocator)
    scriptContent = FileUtil.loadFile(paths.gpgPinentryAppLauncher.toFile())
    assertScriptContentStructure(scriptContent)
  }

  private fun assertScriptContentStructure(scriptContent: String) {
    assertTrue(scriptContent.isNotBlank())
    assertFalse(scriptContent.contains("\r"))
    for (line in scriptContent.lines()) {
      assertFalse(line.isBlank())
      val firstTwoChars = line.take(2)
      assertTrue(firstTwoChars.all { it.isWhitespace() }
                 || firstTwoChars.all { !it.isWhitespace() })
    }
  }

  private fun requestPasswordAndAssert(paths: GpgAgentPaths) {
    PinentryService.getInstance(project).use { pinentryData ->
      val keyPassword = PinentryTestUtil.generatePassword(Random.nextInt(4, 200))
      setUiRequester { keyPassword }
      val output = requestPassword(paths, pinentryData)

      val passwordPrefix = "D "
      val receivedPassword = output.find { it.startsWith(passwordPrefix) }?.substringAfter(passwordPrefix)
      assertEquals("Received $output", keyPassword, receivedPassword)
    }
  }

  private fun PinentryService.use(block: PinentryService.(PinentryService.PinentryData) -> Unit) {
    try {
      val pinentryData = startSession()
      assertNotNull(pinentryData)
      block(pinentryData!!)
    }
    catch (e: BindException) {
      logger<PinentryExecutionTest>().warn(e)
    }
    finally {
      stopSession()
    }
  }

  private fun requestPassword(paths: GpgAgentPaths, pinentryData: PinentryService.PinentryData): List<@NlsSafe String> {
    val cmd = GeneralCommandLine(paths.gpgPinentryAppLauncherConfigPath)
      .withEnvironment(PinentryService.PINENTRY_USER_DATA_ENV, pinentryData.toEnv())

    val process = object : CapturingProcessHandler.Silent(cmd) {
      override fun createProcessAdapter(processOutput: ProcessOutput): CapturingProcessAdapter? {
        return object : CapturingProcessAdapter(processOutput) {
          val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            super.onTextAvailable(event, outputType)
            val receivedText = event.text
            if (receivedText != null && outputType == ProcessOutputTypes.STDOUT) {
              replyOn(receivedText)
            }
          }

          private fun replyOn(text: String) {
            if (text.startsWith("OK")) {
              writer.write("GETPIN\n")
              writer.flush()
            }
            if (text.startsWith("D")) {
              writer.write("BYE\n")
              writer.flush()
            }
          }

          override fun processTerminated(event: ProcessEvent) {
            writer.use { super.processTerminated(event) }
          }
        }
      }
    }.runProcess(10000, true)
    val output = process.stdoutLines
    var errLines = process.stderrLines
    if (errLines.isNotEmpty()) {
      LOG.warn("Error output: $errLines")
    }
    return output
  }

  private inner class TestGpgPathLocator : GpgAgentPathsLocator {
    override fun resolvePaths(): GpgAgentPaths? {
      val gpgAgentHome = projectNioRoot.resolve(GPG_HOME_DIR).createDirectories()
      val gpgAgentConf = gpgAgentHome.resolve(GPG_AGENT_CONF_FILE_NAME)
      val gpgAgentConfBackup = gpgAgentHome.resolve(GPG_AGENT_CONF_BACKUP_FILE_NAME)
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)

      return GpgAgentPaths(gpgAgentHome, gpgAgentConf, gpgAgentConfBackup,
                           gpgPinentryAppLauncher, gpgPinentryAppLauncher.toAbsolutePath().toString())
    }

  }

  companion object {
    private val LOG = logger<PinentryExecutionTest>()
  }
}
