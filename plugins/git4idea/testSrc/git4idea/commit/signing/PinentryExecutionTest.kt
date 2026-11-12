// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.project.stateStore
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.io.createDirectories
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_HOME_DIR
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.PINENTRY_LAUNCHER_FILE_NAME
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableManager
import git4idea.repo.GitProjectConfigurationCache
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.BindException
import java.nio.charset.StandardCharsets
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

@TestApplication
class PinentryExecutionTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()
  private val project: Project get() = context.project

  @BeforeEach
  fun setUp() {
    IoTestUtil.assumeUnix()

    context.git("config commit.gpgSign true")
    context.git("config user.signingkey 0A46826A!")
    GitProjectConfigurationCache.getInstance(project).clearCache()
    val enabled = GpgAgentConfigurator.isEnabled(project, GitExecutableManager.getInstance().getExecutable(project))
    assumeTrue(enabled, "GpgAgentConfigurator should be enabled")
  }

  @Test
  fun `test pinentry communication without gpg agent configuration`() {
    val pathLocator = createPathLocator()
    val paths = pathLocator.resolvePaths()!!
    configureGpgAgent(paths)

    requestPasswordAndAssert(paths)
  }

  @Test
  fun `test pinentry communication with existing gpg agent configuration`() {
    val pathLocator = createPathLocator()
    val paths = pathLocator.resolvePaths()!!
    paths.gpgAgentConf.writeText("${GpgAgentConfig.PINENTRY_PROGRAM} /usr/local/bin/pinentry")
    configureGpgAgent(paths)
    val generatedConfig = paths.gpgAgentConf.readLines()
    assertTrue(generatedConfig.size == 3)
    assertTrue(generatedConfig[0] == "${GpgAgentConfig.PINENTRY_PROGRAM} ${paths.gpgPinentryAppLauncherConfigPath}")

    requestPasswordAndAssert(paths)
  }

  @Test
  fun `test existing gpg agent configuration but without pinentry program specified`() {
    val pathLocator = createPathLocator()
    val paths = pathLocator.resolvePaths()!!
    val allowLoopbackPinentryConfig = "allow-loopback-pinentry"
    val defaultCacheTtlConfig = "${GpgAgentConfig.DEFAULT_CACHE_TTL} ${GpgAgentConfig.DEFAULT_CACHE_TTL_CONF_VALUE}"
    val maxCacheTtlConfig = "${GpgAgentConfig.MAX_CACHE_TTL} ${GpgAgentConfig.MAX_CACHE_TTL_CONF_VALUE}"
    val pinentryConfig = "${GpgAgentConfig.PINENTRY_PROGRAM} ${paths.gpgPinentryAppLauncherConfigPath}"

    paths.gpgAgentConf.writeText("$allowLoopbackPinentryConfig\n$defaultCacheTtlConfig\n$maxCacheTtlConfig")
    configureGpgAgent(paths)
    val generatedConfig = paths.gpgAgentConf.readLines()

    assertThat(generatedConfig).containsSubsequence(listOf(allowLoopbackPinentryConfig, defaultCacheTtlConfig, maxCacheTtlConfig, pinentryConfig))
  }

  @Test
  fun `test pinentry launcher structure`() {
    val pathLocator = createPathLocator()
    val paths = pathLocator.resolvePaths()!!

    configureGpgAgent(paths)
    var scriptContent = paths.gpgPinentryAppLauncher.readText()
    assertScriptContentStructure(scriptContent)

    paths.gpgPinentryAppLauncher.deleteIfExists()
    paths.gpgPinentryAppLauncher.deleteIfExists()
    paths.gpgAgentConf.deleteIfExists()
    paths.gpgAgentConfBackup.deleteIfExists()

    paths.gpgAgentConf.writeText("${GpgAgentConfig.PINENTRY_PROGRAM} /usr/local/bin/pinentry")
    configureGpgAgent(paths)
    scriptContent = paths.gpgPinentryAppLauncher.readText()
    assertScriptContentStructure(scriptContent)
  }

  @Test
  fun `test config read after pinentry update`() {
    val paths = createPathLocator().resolvePaths()!!
    val pinentryFallback = "/usr/bin/pinentry-old"
    configureGpgAgent(paths, pinentryFallback)
    val config = GpgAgentConfig.readConfig(paths.gpgAgentConf)
    checkNotNull(config)
    assertTrue(config.isIntellijPinentryConfigured(paths))
    assertPinentryFallback(paths, pinentryFallback)
  }

  @Test
  fun `test pinentry shouldn't be configured twice`() {
    assertTrue(GpgAgentConfigurator.getInstance(project).canBeConfigured())
    configureGpgAgent(createPathLocator().resolvePaths()!!)
    assertFalse(GpgAgentConfigurator.getInstance(project).canBeConfigured())
  }

  @Test
  fun `test pinentry fallbak preserved after update`() {
    val pathLocator = createPathLocator()
    val paths = pathLocator.resolvePaths()!!

    val pinetryFallback = "/non-existing-path/with space/pinentry"
    GpgAgentConfig(paths.gpgAgentConf, mapOf(GpgAgentConfig.PINENTRY_PROGRAM to pinetryFallback)).writeToFile()
    configureGpgAgent(paths, pinetryFallback)

    assertPinentryFallback(paths, pinetryFallback)
    paths.gpgPinentryAppLauncher.writeText("irrelevant content")
    runBlocking {
      GpgAgentConfigurator.getInstance(project).updateExistingPinentryLauncher()
    }
    assertPinentryFallback(paths, pinetryFallback)
  }

  private fun assertPinentryFallback(paths: GpgAgentPaths, pinetryFallback: String) {
    val scriptContent = paths.gpgPinentryAppLauncher.readText()
    assertThat(scriptContent).contains("""exec ${CommandLineUtil.posixQuote(pinetryFallback)} "$@"""")
  }

  private fun configureGpgAgent(gpgAgentPaths: GpgAgentPaths, pinetryFallback: String = "pinentry") {
    val config = GpgAgentConfig.readConfig(gpgAgentPaths.gpgAgentConf)
    runBlocking {
      GpgAgentConfigurator.getInstance(project).doConfigure(
        GitExecutableManager.getInstance().getExecutable(project),
        gpgAgentPaths,
        config,
        pinetryFallback
      )
    }
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
      assertEquals(keyPassword, receivedPassword, "Received $output")
    }
  }

  private fun PinentryService.use(block: PinentryService.(PinentryService.PinentryData) -> Unit) {
    try {
      val pinentryData = startSession(null)
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
      override fun createProcessAdapter(processOutput: ProcessOutput): CapturingProcessAdapter {
        return object : CapturingProcessAdapter(processOutput) {
          val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            super.onTextAvailable(event, outputType)
            val receivedText = event.text
            if (receivedText != null && ProcessOutputType.isStdout(outputType)) {
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
    val errLines = process.stderrLines
    if (errLines.isNotEmpty()) {
      LOG.warn("Error output: $errLines")
    }
    return output
  }
  
  private fun createPathLocator() =
    application.service<GpgAgentPathsLocatorFactory>().createPathLocator(project, GitExecutableManager.getInstance().getExecutable(project))
  
  companion object {
    private val LOG = logger<PinentryExecutionTest>()
  }
}

internal class GpgAgentPathsLocatorTestFactory: GpgAgentPathsLocatorFactory {
  override fun createPathLocator(project: Project, executor: GitExecutable): GpgAgentPathsLocator = object : GpgAgentPathsLocator {
    override fun resolvePaths(): GpgAgentPaths {
      val gpgAgentHome = project.stateStore.projectBasePath.resolve(GPG_HOME_DIR).createDirectories()
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)
      return GpgAgentPaths.create(gpgAgentHome, gpgPinentryAppLauncher.toAbsolutePath().toString())
    }
  }
}
